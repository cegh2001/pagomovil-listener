package com.cegh.pagomovillistener.parser

import com.cegh.pagomovillistener.domain.model.BankType
import com.cegh.pagomovillistener.domain.parser.BdvPaymentParser
import com.cegh.pagomovillistener.domain.parser.BfcPaymentParser
import com.cegh.pagomovillistener.domain.parser.CompositePaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositePaymentParserTest {

    private val parser = CompositePaymentParser(
        listOf(
            BdvPaymentParser(),
            BfcPaymentParser()
        )
    )

    @Test
    fun parseBdvNotificationFromRealScreenshot() {
        val title = "PagomovilBDV recibido"
        val body = "Recibiste un PagomovilBDV por Bs.1,00 del 0412-9897838 Ref: 334023334023 en fecha 11-11-26 hora: 22:29."

        assertTrue(parser.canParse(title, body))
        val payment = parser.parse(title, body)

        assertNotNull(payment)
        assertEquals(BankType.BDV, payment?.bank)
        assertEquals(1.00, payment?.amountBs ?: 0.0, 0.001)
        assertEquals("1,00", payment?.rawAmount)
        assertEquals("0412-9897838", payment?.phoneNumber)
        assertEquals("0412", payment?.phonePrefix)
        assertEquals("7838", payment?.phoneLastDigits)
        assertEquals("334023334023", payment?.reference)
        assertEquals("11-11-26", payment?.date)
        assertEquals("22:29", payment?.time)
    }

    @Test
    fun parseBdvNotificationWithThousandsAmount() {
        val title = "PagomovilBDV recibido"
        val body = "Recibiste un PagomovilBDV por Bs. 1.550,50 del 0424-1112233 Ref: 987654321 en fecha 12-11-26 hora: 14:05."

        val payment = parser.parse(title, body)

        assertNotNull(payment)
        assertEquals(1550.50, payment?.amountBs ?: 0.0, 0.001)
        assertEquals("0424-1112233", payment?.phoneNumber)
        assertEquals("0424", payment?.phonePrefix)
        assertEquals("2233", payment?.phoneLastDigits)
        assertEquals("987654321", payment?.reference)
    }

    @Test
    fun parseBfcNotificationFromRealScreenshot() {
        val title = "Pago recibido"
        val body = "Ha recibido un Pago por Bs. 35,00 del Celular 0414***9152 a traves del servicio TUBFC 11/11/2026"

        assertTrue(parser.canParse(title, body))
        val payment = parser.parse(title, body)

        assertNotNull(payment)
        assertEquals(BankType.BFC, payment?.bank)
        assertEquals(35.00, payment?.amountBs ?: 0.0, 0.001)
        assertEquals("35,00", payment?.rawAmount)
        assertEquals("0414***9152", payment?.phoneNumber)
        assertEquals("0414", payment?.phonePrefix)
        assertEquals("9152", payment?.phoneLastDigits)
        assertNull(payment?.reference)
        assertEquals("11/11/2026", payment?.date)
    }

    @Test
    fun parseUnrelatedNotificationShouldReturnNull() {
        val title = "Dana Mi Princesa"
        val body = "Ya"

        val canParse = parser.canParse(title, body)
        val payment = parser.parse(title, body)

        assertEquals(false, canParse)
        assertNull(payment)
    }
}
