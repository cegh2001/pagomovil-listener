package com.cegh.pagomovillistener.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cegh.pagomovillistener.data.repository.PaymentRepository

class OutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = PaymentRepository(appContext)

    override suspend fun doWork(): Result {
        return try {
            val success = repository.syncPendingPayments()
            if (success) {
                Result.success()
            } else {
                if (runAttemptCount < 10) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < 10) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
