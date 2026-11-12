package com.cegh.pagomovillistener.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.model.OutboxStatus
import com.cegh.pagomovillistener.domain.model.PaymentNotification

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bank: String,
    val amountBs: Double,
    val rawAmount: String,
    val phoneNumber: String,
    val phonePrefix: String?,
    val phoneLastDigits: String?,
    val reference: String?,
    val date: String,
    val time: String?,
    val rawTitle: String,
    val rawBody: String,
    val receivedAt: Long,
    val syncStatus: String,
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null
) {
    fun toDomain(): PaymentNotification {
        return PaymentNotification(
            id = id,
            bank = runCatching { BankType.valueOf(bank) }.getOrDefault(BankType.UNKNOWN),
            amountBs = amountBs,
            rawAmount = rawAmount,
            phoneNumber = phoneNumber,
            phonePrefix = phonePrefix,
            phoneLastDigits = phoneLastDigits,
            reference = reference,
            date = date,
            time = time,
            rawTitle = rawTitle,
            rawBody = rawBody,
            receivedAt = receivedAt,
            syncStatus = runCatching { OutboxStatus.valueOf(syncStatus) }.getOrDefault(OutboxStatus.PENDING),
            syncAttempts = syncAttempts,
            lastSyncError = lastSyncError
        )
    }

    companion object {
        fun fromDomain(domain: PaymentNotification): PaymentEntity {
            return PaymentEntity(
                id = domain.id,
                bank = domain.bank.name,
                amountBs = domain.amountBs,
                rawAmount = domain.rawAmount,
                phoneNumber = domain.phoneNumber,
                phonePrefix = domain.phonePrefix,
                phoneLastDigits = domain.phoneLastDigits,
                reference = domain.reference,
                date = domain.date,
                time = domain.time,
                rawTitle = domain.rawTitle,
                rawBody = domain.rawBody,
                receivedAt = domain.receivedAt,
                syncStatus = domain.syncStatus.name,
                syncAttempts = domain.syncAttempts,
                lastSyncError = domain.lastSyncError
            )
        }
    }
}
