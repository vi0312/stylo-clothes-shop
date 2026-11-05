package com.stylo.shop.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `money uses Vietnamese grouping and no decimals`() {
        assertEquals("1.250.000 ₫", Formatters.money(1_250_000))
        assertEquals("0 ₫", Formatters.money(0))
    }

    @Test
    fun `timestamps render in Asia Ho Chi Minh`() {
        assertEquals("04/11/2026 10:12", Formatters.dateTime("2026-11-04T03:12:45.123Z"))
        assertEquals("04/11/2026", Formatters.date("2026-11-04T03:12:45.123Z"))
    }
}
