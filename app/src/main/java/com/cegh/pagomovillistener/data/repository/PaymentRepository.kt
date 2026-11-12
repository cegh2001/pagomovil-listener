package com.cegh.pagomovillistener.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cegh.pagomovillistener.data.local.AppDatabase
import com.cegh.pagomovillistener.data.local.PaymentDao
import com.cegh.pagomovillistener.data.local.PaymentEntity
import com.cegh.pagomovillistener.data.remote.PaymentApiClient
import com.cegh.pagomovillistener.data.settings.AppPreferences
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import com.cegh.pagomovillistener.service.OutboxWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PaymentRepository(
    private val context: Context,
    private val paymentDao: PaymentDao = AppDatabase.getInstance(context).paymentDao(),
    private val apiClient: PaymentApiClient = PaymentApiClient(),
    private val preferences: AppPreferences = AppPreferences(context)
) {

    val allPaymentsFlow: Flow<List<PaymentNotification>> = paymentDao.getAllPaymentsFlow()
        .map { list -> list.map { it.toDomain() } }

    /**
     * Local-First Outbox Pattern:
     * 1. Save immediately to local Room DB as PENDING.
     * 2. Attempt immediate HTTP POST.
     * 3. On success, mark as SYNCED.
     * 4. On failure or offline, mark as FAILED and schedule WorkManager.
     */
    suspend fun saveAndProcess(payment: PaymentNotification): Long = withContext(Dispatchers.IO) {
        val entity = PaymentEntity.fromDomain(payment)
        val insertedId = paymentDao.insert(entity)
        val savedPayment = payment.copy(id = insertedId)

        val targetUrl = preferences.webhookUrl
        val secret = preferences.webhookSecret

        val result = apiClient.postPayment(targetUrl, secret, savedPayment)

        if (result.isSuccess) {
            paymentDao.markAsSynced(insertedId)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown network error"
            paymentDao.markAsFailed(insertedId, errorMsg)
            scheduleOutboxWorker()
        }

        insertedId
    }

    /**
     * Worker method to synchronize all pending or failed payments.
     */
    suspend fun syncPendingPayments(): Boolean = withContext(Dispatchers.IO) {
        val pendingList = paymentDao.getPendingPayments()
        if (pendingList.isEmpty()) {
            return@withContext true
        }

        val targetUrl = preferences.webhookUrl
        val secret = preferences.webhookSecret

        var allSucceeded = true

        for (item in pendingList) {
            val domain = item.toDomain()
            val result = apiClient.postPayment(targetUrl, secret, domain)
            if (result.isSuccess) {
                paymentDao.markAsSynced(item.id)
            } else {
                allSucceeded = false
                val errorMsg = result.exceptionOrNull()?.message ?: "Retry sync failed"
                paymentDao.markAsFailed(item.id, errorMsg)
            }
        }

        allSucceeded
    }

    /**
     * Enqueues WorkManager with connected network constraint and exponential backoff.
     */
    fun scheduleOutboxWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_OUTBOX_SYNC,
            ExistingWorkPolicy.REPLACE,
            syncWork
        )
    }

    suspend fun deleteSyncedOlderThan(days: Int): Int = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        paymentDao.deleteSyncedOlderThan(threshold)
    }

    companion object {
        const val WORK_OUTBOX_SYNC = "work_outbox_sync"
    }
}
