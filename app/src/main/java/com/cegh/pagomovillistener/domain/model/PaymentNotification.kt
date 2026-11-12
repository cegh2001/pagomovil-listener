package com.cegh.pagomovillistener.domain.model

data class PaymentNotification(
    val id: Long = 0,
    val bank: BankType,
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
    val receivedAt: Long = System.currentTimeMillis(),
    val syncStatus: OutboxStatus = OutboxStatus.PENDING,
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null
)
