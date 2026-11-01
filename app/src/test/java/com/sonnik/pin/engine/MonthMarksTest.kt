package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthMarksTest {
    @Test
    fun `значки дня как в календаре`() {
        val marks = MonthMarks.build(
            listOf(
                MarkItem(1, MarkKind.OPEN, EntryType.ONE_TIME, important = true),
                MarkItem(1, MarkKind.PROJECTED, EntryType.RECURRING),
                MarkItem(1, MarkKind.DONE, EntryType.ONE_TIME),
                MarkItem(1, MarkKind.DONE, EntryType.HABIT),
                MarkItem(1, MarkKind.OPEN, EntryType.BIRTHDAY),
                MarkItem(2, MarkKind.PROJECTED, EntryType.HOLIDAY),
                MarkItem(3, MarkKind.DONE, EntryType.ONE_TIME, important = true),
            ),
        )
        assertEquals(DayMarks(listOf(MarkKind.OPEN, MarkKind.PROJECTED), important = true, birthday = true, holiday = false), marks[1])
        assertEquals(DayMarks(emptyList(), important = false, birthday = false, holiday = true), marks[2])
        assertEquals(DayMarks(listOf(MarkKind.DONE), important = false, birthday = false, holiday = false), marks[3])
        // День рождения с высоким приоритетом — только 🎂, без «!».
        assertEquals(
            DayMarks(emptyList(), important = false, birthday = true, holiday = false),
            MonthMarks.build(listOf(MarkItem(5, MarkKind.OPEN, EntryType.BIRTHDAY, important = true)))[5],
        )
    }

    @Test
    fun `подписи дней рождения и праздников`() {
        val d14 = java.time.LocalDate.of(2026, 11, 14).toEpochDay()
        val d4 = java.time.LocalDate.of(2026, 11, 4).toEpochDay()
        val events = listOf(
            CalendarEvent(d14, true, "Аня", 51),
            CalendarEvent(d4, false, "День народного единства"),
            CalendarEvent(d4 - 2, true, "Прошедший"),
            CalendarEvent(d14, true, "Аня", 51),
        )
        val up = CalendarEvent.upcoming(events, fromDay = d4)
        assertEquals(listOf("🎉 4 — День народного единства", "🎂 14 — Аня, 51"), up.map { it.label() })
    }

    @Test
    fun `тепловая карта и неделя`() {
        assertEquals(listOf(0, 1, 2, 3, 3, 4, 4), listOf(0, 1, 2, 3, 4, 5, 9).map(Heat::level))
        assertEquals(0f, Heat.alpha(0), 0.001f)
        val wed = java.time.LocalDate.of(2026, 11, 4).toEpochDay()
        val mon = java.time.LocalDate.of(2026, 11, 2).toEpochDay()
        assertEquals(mon..mon + 6, Heat.weekOf(wed))
        assertEquals(mon..mon + 6, Heat.weekOf(mon + 6))
    }
}
