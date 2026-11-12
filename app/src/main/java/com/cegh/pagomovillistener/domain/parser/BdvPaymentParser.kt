package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import java.util.regex.Pattern

class BdvPaymentParser : PaymentParser {

    // Example: Recibiste un PagomovilBDV por Bs.1,00 del 0412-9897838 Ref: 334023334023 en fecha 11-11-26 hora: 22:29.
    private val pattern = Pattern.compile(
        """(?i)por\s+Bs\.?\s*([0-9\.,]+)\s+del\s+([0-9\-*+]+)\s+Ref:\s*([0-9a-zA-Z]+)\s+en\s+fecha\s+([0-9\-\/]+)(?:\s+hora:\s*([0-9:]+))?"""
    )

    override fun canParse(title: String, body: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerBody = body.lowercase()
        return (lowerTitle.contains("pagomovilbdv") || lowerTitle.contains("bdv") || lowerBody.contains("pagomovilbdv")) &&
                (lowerBody.contains("recibiste") || lowerBody.contains("recibido"))
    }

    override fun parse(title: String, body: String): PaymentNotification? {
        val matcher = pattern.matcher(body)
        if (!matcher.find()) {
            return null
        }

        val rawAmount = matcher.group(1).orEmpty().trim()
        val phoneNumber = matcher.group(2).orEmpty().trim()
        val reference = matcher.group(3).orEmpty().trim()
        val date = matcher.group(4).orEmpty().trim()
        val time = matcher.group(5)?.trim()

        val amount = parseAmount(rawAmount)
        val (prefix, lastDigits) = extractPhoneParts(phoneNumber)

        return PaymentNotification(
            bank = BankType.BDV,
            amountBs = amount,
            rawAmount = rawAmount,
            phoneNumber = phoneNumber,
            phonePrefix = prefix,
            phoneLastDigits = lastDigits,
            reference = reference,
            date = date,
            time = time,
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
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        val prefix = if (digitsOnly.length >= 4) digitsOnly.substring(0, 4) else null
        val lastDigits = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else digitsOnly
        return Pair(prefix, lastDigits)
    }
}
