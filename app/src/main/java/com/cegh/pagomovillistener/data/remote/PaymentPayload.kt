package com.cegh.pagomovillistener.data.remote

import com.cegh.pagomovillistener.domain.model.PaymentNotification
import com.google.gson.annotations.SerializedName

data class PaymentPayload(
    @SerializedName("id")
    val id: Long,

    @SerializedName("bank")
    val bank: String,

    @SerializedName("amount_bs")
    val amountBs: Double,

    @SerializedName("raw_amount")
    val rawAmount: String,

    @SerializedName("phone_number")
    val phoneNumber: String,

    @SerializedName("phone_prefix")
    val phonePrefix: String?,

    @SerializedName("phone_last_digits")
    val phoneLastDigits: String?,

    @SerializedName("reference")
    val reference: String?,

    @SerializedName("date")
    val date: String,

    @SerializedName("time")
    val time: String?,

    @SerializedName("raw_title")
    val rawTitle: String,

    @SerializedName("raw_body")
    val rawBody: String,

    @SerializedName("received_at")
    val receivedAt: Long
) {
    companion object {
        fun fromDomain(payment: PaymentNotification): PaymentPayload {
            return PaymentPayload(
                id = payment.id,
                bank = payment.bank.name,
                amountBs = payment.amountBs,
                rawAmount = payment.rawAmount,
                phoneNumber = payment.phoneNumber,
                phonePrefix = payment.phonePrefix,
                phoneLastDigits = payment.phoneLastDigits,
                reference = payment.reference,
                date = payment.date,
                time = payment.time,
                rawTitle = payment.rawTitle,
                rawBody = payment.rawBody,
                receivedAt = payment.receivedAt
            )
        }
    }
}
