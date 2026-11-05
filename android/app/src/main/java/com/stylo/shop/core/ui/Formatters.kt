package com.stylo.shop.core.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val vietnamZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val vietnameseLocale = Locale.forLanguageTag("vi-VN")
    private val dateTimePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", vietnameseLocale)
    private val datePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy", vietnameseLocale)

    fun money(amount: Long): String =
        NumberFormat.getNumberInstance(vietnameseLocale).format(amount) + "\u00A0₫"

    fun dateTime(isoUtc: String): String =
        Instant.parse(isoUtc).atZone(vietnamZone).format(dateTimePattern)

    fun date(isoUtc: String): String =
        Instant.parse(isoUtc).atZone(vietnamZone).format(datePattern)
}
