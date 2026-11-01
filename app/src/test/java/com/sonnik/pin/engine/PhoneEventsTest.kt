package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class PhoneEventsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val nov5 = LocalDate.of(2026, 11, 5).toEpochDay()

    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun utcDay(d: Int) = LocalDate.of(2026, 11, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `обычное событие — один день и время`() {
        val e = PhoneEvent(1, "Стоматолог", at(5, 10), at(5, 11, 30), allDay = false)
        assertEquals(nov5..nov5, PhoneEvents.days(e, zone))
        assertEquals("10:00–11:30", PhoneEvents.describe(e, nov5, zone))
    }

    @Test
    fun `весь день в UTC не съезжает на соседний день`() {
        val e = PhoneEvent(2, "Отпуск", utcDay(5), utcDay(8), allDay = true)
        assertEquals(nov5..nov5 + 2, PhoneEvents.days(e, zone))
        assertEquals("весь день", PhoneEvents.describe(e, nov5 + 1, zone))
    }

    @Test
    fun `ночное событие — с и до`() {
        val e = PhoneEvent(3, "Поезд", at(5, 22), at(6, 1), allDay = false)
        assertEquals(nov5..nov5 + 1, PhoneEvents.days(e, zone))
        assertEquals("с 22:00", PhoneEvents.describe(e, nov5, zone))
        assertEquals("до 01:00", PhoneEvents.describe(e, nov5 + 1, zone))
    }

    @Test
    fun `по дням — сначала весь день, потом по времени, в пределах отрезка`() {
        val a = PhoneEvent(1, "Созвон", at(5, 15), at(5, 16), allDay = false)
        val b = PhoneEvent(2, "Отпуск", utcDay(4), utcDay(7), allDay = true)
        val c = PhoneEvent(3, "Завтрак", at(5, 9), at(5, 10), allDay = false)
        val map = PhoneEvents.byDay(listOf(a, b, c), zone, nov5, nov5 + 10)
        assertEquals(listOf("Отпуск", "Завтрак", "Созвон"), map.getValue(nov5).map { it.title })
        assertEquals(setOf(nov5, nov5 + 1), map.keys)
    }
}
