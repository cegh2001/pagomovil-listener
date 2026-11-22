package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.model.PaymentNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class BfcPaymentParser(
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PaymentParser {

    // Format 1 (Pago Móvil): Ha recibido un Pago por Bs. 35,00 del Celular 0414***9152 a traves del servicio TUBFC 11/11/2026
    private val pagoMovilPattern = Pattern.compile(
        """(?i)por\s+Bs\.?\s*([0-9\.,]+)\s+del\s+Celular\s+([0-9\-*+]+)(?:.*?TUBFC)?(?:\s+([0-9]{2}[\/\-][0-9]{2}[\/\-][0-9]{2,4}))?"""
    )

    // Format 2 (Bank Transfer): Has recibido una Transferencia Otros Bancos de Bs. 20.000,00 a tu cuenta 0151*********20 el dia 21/11/2026. La referencia del pago es 57677639
    private val transferPattern = Pattern.compile(
        """(?i)transferencia.*?(?:de|por)\s+Bs\.?\s*([0-9\.,]+).*?el\s+d[ií]a\s+([0-9]{2}[\/\-][0-9]{2}[\/\-][0-9]{2,4}).*?referencia(?:\s+del\s+pago)?\s*(?:es\s*:?\s*|:\s*)([0-9a-zA-Z]+)"""
    )

    override fun canParse(title: String, body: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerBody = body.lowercase()
        val isBfcPagoMovil = (lowerTitle.contains("pago recibido") || lowerBody.contains("tubfc") || lowerTitle.contains("bfc")) &&
                (lowerBody.contains("ha recibido un pago") || lowerBody.contains("pago por bs"))
        val isBfcTransfer = (lowerTitle.contains("transferencia recibida") || lowerTitle.contains("bfc") || lowerBody.contains("0151")) &&
                (lowerBody.contains("has recibido una transferencia") || lowerBody.contains("ha recibido una transferencia")) &&
                (lowerBody.contains("0151") || lowerBody.contains("la referencia del pago es") || lowerTitle.contains("bfc") || lowerBody.contains("tubfc"))
        return isBfcPagoMovil || isBfcTransfer
    }

    override fun parse(title: String, body: String): PaymentNotification? {
        val pagoMovilMatcher = pagoMovilPattern.matcher(body)
        if (pagoMovilMatcher.find()) {
            val rawAmount = pagoMovilMatcher.group(1).orEmpty().trim().trimEnd('.')
            val phoneNumber = pagoMovilMatcher.group(2).orEmpty().trim()
            val date = pagoMovilMatcher.group(3)?.trim() ?: ""

            val amount = parseAmount(rawAmount)
            val (prefix, lastDigits) = extractPhoneParts(phoneNumber)

            // BFC Pago Móvil push notifications do not include reference number
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

        val transferMatcher = transferPattern.matcher(body)
        if (transferMatcher.find()) {
            val rawAmount = transferMatcher.group(1).orEmpty().trim().trimEnd('.')
            val date = transferMatcher.group(2).orEmpty().trim()
            val reference = transferMatcher.group(3).orEmpty().trim()

            val now = clock()
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))

            return PaymentNotification(
                bank = BankType.BFC,
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
        val prefix = if (phone.length >= 4) phone.substring(0, 4) else null
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        val lastDigits = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else digitsOnly
        return Pair(prefix, lastDigits)
    }
}
