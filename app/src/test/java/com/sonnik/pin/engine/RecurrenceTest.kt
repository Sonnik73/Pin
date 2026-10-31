package com.sonnik.pin.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    @Test
    fun `разбор и запись правил туда и обратно`() {
        val rules = listOf(
            RepeatRule.Daily,
            RepeatRule.EveryNDays(3),
            RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            RepeatRule.Monthly(15),
            RepeatRule.Monthly(null),
            RepeatRule.Yearly(3, 8),
        )
        for (rule in rules) assertEquals(rule, RepeatRule.parse(RepeatRule.format(rule)))
        assertEquals("weekly mon,fri", RepeatRule.format(rules[2]))
        assertEquals("yearly 03-08", RepeatRule.format(rules[5]))
    }

    @Test
    fun `разбор терпит регистр, пробелы и мусор`() {
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), RepeatRule.parse("  Weekly Monday, wed "))
        assertEquals(RepeatRule.Daily, RepeatRule.parse("every 1 day"))
        assertNull(RepeatRule.parse("sometimes"))
        assertNull(RepeatRule.parse("monthly 40"))
        assertNull(RepeatRule.parse("yearly 02-30"))
        assertNull(RepeatRule.parse(""))
        assertNull(RepeatRule.parse(null))
    }

    @Test
    fun `каждые N дней считаются от якоря`() {
        val rule = RepeatRule.EveryNDays(3)
        val anchor = d(2026, 11, 1)
        assertEquals(anchor, rule.nextAfter(anchor - 5, anchor))
        assertEquals(anchor + 3, rule.nextAfter(anchor, anchor))
        assertEquals(anchor + 3, rule.nextAfter(anchor + 2, anchor))
        assertEquals(anchor + 6, rule.nextAfter(anchor + 3, anchor))
    }

    @Test
    fun `по дням недели`() {
        val rule = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        // 1 ноября 2026 — воскресенье.
        assertEquals(d(2026, 11, 2), rule.nextAfter(d(2026, 11, 1), 0))
        assertEquals(d(2026, 11, 5), rule.nextAfter(d(2026, 11, 2), 0))
        assertEquals(d(2026, 11, 9), rule.nextAfter(d(2026, 11, 5), 0))
    }

    @Test
    fun `ежемесячно 31-го и в последний день`() {
        val r31 = RepeatRule.Monthly(31)
        assertEquals(d(2027, 2, 28), r31.nextAfter(d(2027, 1, 31), 0))
        assertEquals(d(2027, 3, 31), r31.nextAfter(d(2027, 2, 28), 0))
        val last = RepeatRule.Monthly(null)
        assertEquals(d(2028, 2, 29), last.nextAfter(d(2028, 2, 1), 0))
        assertEquals(d(2026, 11, 30), last.nextAfter(d(2026, 10, 31), 0))
    }

    @Test
    fun `ежегодно, 29 февраля в невисокосный год`() {
        val rule = RepeatRule.Yearly(2, 29)
        assertEquals(d(2027, 2, 28), rule.nextAfter(d(2026, 3, 1), 0))
        assertEquals(d(2028, 2, 29), rule.nextAfter(d(2027, 2, 28), 0))
        assertEquals(d(2026, 11, 4), RepeatRule.Yearly(11, 4).nextAfter(d(2026, 11, 3), 0))
    }

    @Test
    fun `повторы в отрезке`() {
        val days = RepeatRule.occurrences(RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY)), 0, d(2026, 11, 1), d(2026, 11, 30))
        assertEquals(listOf(6, 13, 20, 27).map { d(2026, 11, it) }, days)
    }

    @Test
    fun `описания по-русски`() {
        assertEquals("каждые 3 дня", RepeatRule.describe(RepeatRule.EveryNDays(3)))
        assertEquals("по будням", RepeatRule.describe(RepeatRule.Weekly(DayOfWeek.entries.take(5).toSet())))
        assertEquals("по пн, пт", RepeatRule.describe(RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))))
        assertEquals("ежегодно 8 марта", RepeatRule.describe(RepeatRule.Yearly(3, 8)))
        assertTrue(RepeatRule.describe(RepeatRule.Monthly(null)).contains("последний"))
    }
}
