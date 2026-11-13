package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.PaymentNotification

class CompositePaymentParser(
    private val parsers: List<PaymentParser> = listOf(
        BdvPaymentParser(),
        BfcPaymentParser(),
        BancamigaPaymentParser()
    )
) : PaymentParser {

    override fun canParse(title: String, body: String): Boolean {
        return parsers.any { it.canParse(title, body) }
    }

    override fun parse(title: String, body: String): PaymentNotification? {
        for (parser in parsers) {
            if (parser.canParse(title, body)) {
                val parsed = parser.parse(title, body)
                if (parsed != null) {
                    return parsed
                }
            }
        }
        return null
    }
}
