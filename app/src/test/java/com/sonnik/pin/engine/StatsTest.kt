package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    private val today = 1000L

    private fun ev(id: String, event: String, day: Long, onTime: Boolean = true, priority: Int = 1, rollovers: Int = 0, kind: EntryType = EntryType.ONE_TIME) =
        StatEvent(id, event, day, kind, priority, rollovers, onTime)

    private val events = listOf(
        // День 995: одна сделана вовремя, одна перенесена.
        ev("a", "DONE", 995),
        ev("b", "ROLLED", 995, rollovers = 1),
        // День 996: перенесённая сделана с опозданием, важная — вовремя.
        ev("b", "DONE", 996, onTime = false, rollovers = 1),
        ev("c", "DONE", 996, priority = 2),
        // День 997: ежедневная пропущена (без ночей переноса).
        ev("d", "MISSED", 997, kind = EntryType.RECURRING),
        // День 998, 999: всё сделано.
        ev("d", "DONE", 998, kind = EntryType.RECURRING),
        ev("d", "DONE", 999, kind = EntryType.RECURRING),
        // Праздники в статистику не попадают.
        ev("h", "MISSED", 999, kind = EntryType.HOLIDAY),
    )

    @Test
    fun `цифры по дням`() {
        val days = Stats.days(events, 995, 1000, openByDay = mapOf(1000L to 2))
        assertEquals(listOf(2, 2, 1, 1, 1, 2), days.map { it.planned })
        assertEquals(listOf(1, 2, 0, 1, 1, 0), days.map { it.done })
        assertEquals(listOf(1, 1, 0, 1, 1, 0), days.map { it.doneOnTime })
        assertEquals(listOf(1, 0, 0, 0, 0, 0), days.map { it.rolledOver })
        assertEquals(listOf(0, 0, 1, 0, 0, 0), days.map { it.missed })
        assertEquals(listOf(1, 0, 1, 0, 0, 0), days.map { it.debts })
        assertEquals(1, days[1].importantDone)
    }

    @Test
    fun `серии без долгов`() {
        // Долги в 995 и 997; без долгов 996, 998, 999 → текущая 2, лучшая 2.
        assertEquals(2 to 2, Stats.streaks(events, today))
        assertEquals(0 to 0, Stats.streaks(emptyList(), today))
        assertEquals(0 to 1, Stats.streaks(events, 998))
    }

    @Test
    fun `сводка, типы и теги`() {
        val tags = mapOf("a" to listOf("дом"), "c" to listOf("работа", "дом"))
        val s = Stats.summary(events, 995, 999, today, { tags[it].orEmpty() })
        assertEquals(5, s.done)
        assertEquals(7, s.planned)
        assertEquals(4, s.onTime)
        assertEquals(1, s.rolledOver)
        assertEquals(1, s.missed)
        assertEquals(listOf(EntryType.ONE_TIME to 3, EntryType.RECURRING to 2), s.byKind)
        assertEquals(listOf("" to 3, "дом" to 2, "работа" to 1), s.byTag)
        assertEquals(0.8, s.onTimeShare!!, 1e-9)
    }

    @Test
    fun `группы для графика`() {
        val jan31 = java.time.LocalDate.of(2026, 1, 31).toEpochDay()
        val days = Stats.days(listOf(ev("x", "DONE", jan31), ev("y", "ROLLED", jan31 + 1)), jan31, jan31 + 1)
        val months = Stats.buckets(days, byMonth = true)
        assertEquals(listOf("янв", "фев"), months.map { it.label })
        assertEquals(listOf(1, 0), months.map { it.done })
        assertEquals(listOf(0, 1), months.map { it.debts })
        assertEquals(listOf("31", "1"), Stats.buckets(days, byMonth = false).map { it.label })
    }
}
