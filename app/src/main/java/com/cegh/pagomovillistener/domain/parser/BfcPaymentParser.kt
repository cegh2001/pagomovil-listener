package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import java.util.regex.Pattern

class BfcPaymentParser : PaymentParser {

    // Example: Ha recibido un Pago por Bs. 35,00 del Celular 0414***9152 a traves del servicio TUBFC 11/11/2026
    private val pattern = Pattern.compile(
        """(?i)por\s+Bs\.?\s*([0-9\.,]+)\s+del\s+Celular\s+([0-9\-*+]+)(?:.*?TUBFC)?(?:\s+([0-9]{2}[\/\-][0-9]{2}[\/\-][0-9]{2,4}))?"""
    )

    override fun canParse(title: String, body: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerBody = body.lowercase()
        return (lowerTitle.contains("pago recibido") || lowerBody.contains("tubfc") || lowerTitle.contains("bfc")) &&
                (lowerBody.contains("ha recibido un pago") || lowerBody.contains("pago por bs"))
    }

    override fun parse(title: String, body: String): PaymentNotification? {
        val matcher = pattern.matcher(body)
        if (!matcher.find()) {
            return null
        }

        val rawAmount = matcher.group(1).orEmpty().trim()
        val phoneNumber = matcher.group(2).orEmpty().trim()
        val date = matcher.group(3)?.trim() ?: ""

        val amount = parseAmount(rawAmount)
        val (prefix, lastDigits) = extractPhoneParts(phoneNumber)

        // BFC push notifications do not include reference number
        return PaymentNotification(
            bank = BankType.BFC,
            amountBs = amount,
            rawAmount = rawAmount,
            phoneNumber = phoneNumber,
            phonePrefix = prefix,
            phoneLastDigits = lastDigits,
            reference = null,
            date = date,
            time = null,
            rawTitle = title,
            rawBody = body
        )
    }

    private fun parseAmount(raw: String): Double {
        val normalized = raw
            .replace(".", "")
            .replace(",", ".")
        return normalized.toDoubleOrNull() ?: 0.0
    }

    private fun extractPhoneParts(phone: String): Pair<String?, String?> {
        val prefix = if (phone.length >= 4) phone.substring(0, 4) else null
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        val lastDigits = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else digitsOnly
        return Pair(prefix, lastDigits)
    }
}
