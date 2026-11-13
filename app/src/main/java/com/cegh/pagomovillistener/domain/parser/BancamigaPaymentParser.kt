package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class BancamigaPaymentParser(
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PaymentParser {

    // Example: Has recibido un Pago Móvil desde el 04129897838 por Bs. 100,00.
    private val pattern = Pattern.compile(
        """(?i)desde\s+el\s+([0-9\-+*]+)\s+por\s+Bs\.?\s*([0-9\.,]+)"""
    )

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun canParse(title: String, body: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerBody = body.lowercase()
        return (lowerTitle.contains("bancamiga") || lowerBody.contains("bancamiga")) &&
                (lowerBody.contains("has recibido un pago") || lowerBody.contains("pago móvil") || lowerBody.contains("pago movil"))
    }

    override fun parse(title: String, body: String): PaymentNotification? {
        val matcher = pattern.matcher(body)
        if (!matcher.find()) {
            return null
        }

        val phoneNumber = matcher.group(1).orEmpty().trim()
        val rawAmountWithDot = matcher.group(2).orEmpty().trim()
        val rawAmount = rawAmountWithDot.trimEnd('.')

        val amount = parseAmount(rawAmount)
        val (prefix, lastDigits) = extractPhoneParts(phoneNumber)

        val now = clock()
        val dateObj = Date(now)
        val formattedDate = dateFormat.format(dateObj)
        val formattedTime = timeFormat.format(dateObj)

        // Bancamiga push notifications do not include reference number
        return PaymentNotification(
            bank = BankType.BANCAMIGA,
            amountBs = amount,
            rawAmount = rawAmount,
            phoneNumber = phoneNumber,
            phonePrefix = prefix,
            phoneLastDigits = lastDigits,
            reference = null,
            date = formattedDate,
            time = formattedTime,
            rawTitle = title,
            rawBody = body,
            receivedAt = now
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
