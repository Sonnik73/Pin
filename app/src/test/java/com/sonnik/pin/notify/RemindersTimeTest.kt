package com.sonnik.pin.notify

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Через N мин» в тексте напоминания — от момента срабатывания будильника, а не от системных часов. */
class RemindersTimeTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `минуты до времени задачи от момента срабатывания`() {
        // 13 ноября 2026, 09:30 по Москве — напоминание на 10:00.
        val at = java.time.LocalDateTime.of(2026, 11, 13, 9, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(30, RemindersTime.minutesUntil(10 * 60, at, zone))
    }

    @Test
    fun `уже прошло — ноль или меньше`() {
        val at = java.time.LocalDateTime.of(2026, 11, 13, 10, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals(-5, RemindersTime.minutesUntil(10 * 60, at, zone))
    }

    @Test
    fun `граница дня не мешает — время дня считается в поясе`() {
        // 03:50 — ещё «вчера» по границе 04:00, но время дня 04:00 через 10 минут.
        val at = java.time.LocalDateTime.of(2026, 11, 13, 3, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals(10, RemindersTime.minutesUntil(4 * 60, at, zone))
    }
}
