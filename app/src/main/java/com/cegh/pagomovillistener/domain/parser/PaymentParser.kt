package com.cegh.pagomovillistener.domain.parser

import com.cegh.pagomovillistener.domain.model.PaymentNotification

interface PaymentParser {
    fun canParse(title: String, body: String): Boolean
    fun parse(title: String, body: String): PaymentNotification?
}
