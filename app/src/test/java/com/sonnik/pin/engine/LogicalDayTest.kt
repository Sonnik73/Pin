package com.sonnik.pin.engine

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicalDayTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    private fun clockAt(dateTime: LocalDateTime): Clock = Clock.fixed(dateTime.atZone(moscow).toInstant(), moscow)

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `до границы дня считается вчерашним днём`() {
        val day = LogicalDay(clockAt(LocalDateTime.of(2026, 11, 2, 1, 30)))
        assertEquals(epoch(2026, 11, 1), day.today())
    }

    @Test
    fun `ровно в 04-00 начинается новый день`() {
        assertEquals(epoch(2026, 11, 2), LogicalDay(clockAt(LocalDateTime.of(2026, 11, 2, 4, 0))).today())
        assertEquals(epoch(2026, 11, 1), LogicalDay(clockAt(LocalDateTime.of(2026, 11, 2, 3, 59))).today())
    }

    @Test
    fun `граница в полночь — обычный календарный день`() {
        val day = LogicalDay(clockAt(LocalDateTime.of(2026, 11, 2, 0, 1)), boundaryMinutes = 0)
        assertEquals(epoch(2026, 11, 2), day.today())
    }

    @Test
    fun `начало дня и следующая граница`() {
        val clock = clockAt(LocalDateTime.of(2026, 11, 2, 12, 0))
        val day = LogicalDay(clock)
        val expectedStart = LocalDateTime.of(2026, 11, 2, 4, 0).atZone(moscow).toInstant().toEpochMilli()
        assertEquals(expectedStart, day.startOf(day.today()))
        assertEquals(expectedStart + 24 * 3600_000L, day.nextBoundary())
        assertEquals(day.today(), day.dayOf(day.startOf(day.today())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `граница вне суток отвергается`() {
        LogicalDay(Clock.systemUTC(), boundaryMinutes = 24 * 60)
    }
}
