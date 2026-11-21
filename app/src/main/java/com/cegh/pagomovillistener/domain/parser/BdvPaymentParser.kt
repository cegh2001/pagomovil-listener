package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class BdvPaymentParser(
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PaymentParser {

    // Format 1 (Interbank): Recibiste un PagomovilBDV por Bs.1,00 del 0412-9897838 Ref: 334023334023 en fecha 11-11-26 hora: 22:29.
    private val interbankPattern = Pattern.compile(
        """(?i)por\s+Bs\.?\s*([0-9\.,]+)\s+del\s+([0-9\-*+]+)\s+Ref:\s*([0-9a-zA-Z]+)\s+en\s+fecha\s+([0-9\-\/]+)(?:\s+hora:\s*([0-9:]+))?"""
    )

    // Format 2 (Named sender / operation number): "Recibiste un PagomovilBDV de ZELENIS BEATRIZ AGUILAR SANCHEZ por Bs.13.797,34 bajo el numero de operacion 008111758337"
    private val operationPattern = Pattern.compile(
        """(?i)por\s+Bs\.?\s*([0-9\.,]+)\s+bajo\s+el\s+n[uú]mero\s+de\s+operaci[oó]n\s*:?\s*([0-9a-zA-Z]+)(?:.*?en\s+fecha\s+([0-9\-\/]+)(?:\s+hora:\s*([0-9:]+))?)?"""
    )

    override fun canParse(title: String, body: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerBody = body.lowercase()
        val isBdv = lowerTitle.contains("pagomovilbdv") ||
                lowerTitle.contains("pagomóvilbdv") ||
                lowerTitle.contains("bdv") ||
                lowerBody.contains("pagomovilbdv") ||
                lowerBody.contains("pagomóvilbdv")
        val isIncoming = lowerBody.contains("recibiste") ||
                lowerBody.contains("recibido") ||
                lowerTitle.contains("recibido")
        return isBdv && isIncoming
    }

    override fun parse(title: String, body: String): PaymentNotification? {
        val interbankMatcher = interbankPattern.matcher(body)
        if (interbankMatcher.find()) {
            val rawAmount = interbankMatcher.group(1).orEmpty().trim().trimEnd('.')
            val phoneNumber = interbankMatcher.group(2).orEmpty().trim()
            val reference = interbankMatcher.group(3).orEmpty().trim()
            val date = interbankMatcher.group(4).orEmpty().trim()
            val time = interbankMatcher.group(5)?.trim()

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

        val operationMatcher = operationPattern.matcher(body)
        if (operationMatcher.find()) {
            val rawAmount = operationMatcher.group(1).orEmpty().trim().trimEnd('.')
            val reference = operationMatcher.group(2).orEmpty().trim()
            val now = clock()
            val dateObj = Date(now)
            val date = operationMatcher.group(3)?.trim()?.takeIf { it.isNotBlank() }
                ?: SimpleDateFormat("dd-MM-yy", Locale.getDefault()).format(dateObj)
            val time = operationMatcher.group(4)?.trim()?.takeIf { it.isNotBlank() }
                ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateObj)

            return PaymentNotification(
                bank = BankType.BDV,
                amountBs = parseAmount(rawAmount),
                rawAmount = rawAmount,
                phoneNumber = "",
                phonePrefix = null,
                phoneLastDigits = null,
                reference = reference,
                date = date,
                time = time,
                rawTitle = title,
                rawBody = body,
                receivedAt = now
            )
        }

        return null
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
