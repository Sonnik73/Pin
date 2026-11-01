package com.sonnik.pin.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitsTest {
    // Понедельник 2 ноября 2026.
    private val monday = LocalDate.of(2026, 11, 2).toEpochDay()

    @Test
    fun `ежедневная — серии и доля`() {
        val h = HabitInfo("h", null, monday, setOf(monday, monday + 1, monday + 3, monday + 4, monday + 5))
        // Сегодня (+6) не отмечена — серия не прервана: +3, +4, +5.
        val s = Habits.stats(h, monday + 6)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
        assertFalse(s.doneToday)
        assertTrue(s.scheduledToday)
        assertEquals(5.0 / 6, s.rate30!!, 1e-9)
        // Пропуск вчера обнуляет текущую.
        assertEquals(0, Habits.stats(h, monday + 7).current)
    }

    @Test
    fun `по дням недели — выходные не ломают серию`() {
        val weekdays = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val done = setOf(monday, monday + 2, monday + 4, monday + 7)
        val h = HabitInfo("h", weekdays, monday, done)
        assertTrue(Habits.isScheduled(h, monday + 2))
        assertFalse(Habits.isScheduled(h, monday + 1))
        assertFalse(Habits.isScheduled(h, monday - 7))
        // Вторник второй недели: пн +7 сделан, серия 4.
        assertEquals(4, Habits.stats(h, monday + 8).current)
        // Отметка в незапланированный день тоже засчитывается.
        assertEquals(5, Habits.stats(h.copy(doneDays = done + (monday + 8)), monday + 8).current)
    }

    @Test
    fun `тепловая карта и счётчики дня`() {
        val h = HabitInfo("h", null, monday, setOf(monday))
        val map = Habits.heatmap(h, monday + 2, weeks = 2)
        assertEquals(2, map.size)
        assertTrue(map.all { it.size == 7 })
        val cell = map[1][0]
        assertEquals(monday, cell.day)
        assertTrue(cell.done)
        assertTrue(map[1][3].future)
        assertFalse(map[0][0].scheduled)
        assertNull(Habits.stats(HabitInfo("x", null, monday + 5, emptySet()), monday + 2).rate30)
        assertEquals(1 to 2, Habits.dayCounts(listOf(h, HabitInfo("y", null, monday, emptySet())), monday))
    }

    @Test
    fun `неделя привычки для кольца`() {
        val wed = java.time.LocalDate.of(2026, 11, 4).toEpochDay()
        val mon = wed - 2
        val daily = HabitInfo("h", null, mon - 30, setOf(mon, wed))
        assertEquals(2 to 7, Habits.week(daily, wed))
        // По понедельникам и средам — план 2 дня.
        val twice = HabitInfo("h", RepeatRule.Weekly(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY)), mon - 30, setOf(wed))
        assertEquals(1 to 2, Habits.week(twice, wed))
        // Начали во вторник — понедельник не в плане.
        assertEquals(0 to 6, Habits.week(HabitInfo("h", null, mon + 1, emptySet()), wed))
    }
}
