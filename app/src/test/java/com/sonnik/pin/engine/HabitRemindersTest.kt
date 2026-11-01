package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HabitRemindersTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val boundary = 4 * 60
    private val mon = LocalDate.of(2026, 11, 2).toEpochDay() // понедельник

    private fun at(day: Long, h: Int, m: Int = 0) = LocalDate.ofEpochDay(day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `напоминание в свой день, пока не отмечено`() {
        val alarm = HabitAlarm(HabitInfo("h", null, mon - 10, emptySet()), "Зарядка", 7 * 60 + 30)
        assertEquals(listOf("h"), Reminders.habitsDue(listOf(alarm), mon, at(mon, 7), at(mon, 8), zone, boundary).map { it.taskId })
        assertEquals(at(mon, 7, 30), Reminders.nextHabit(listOf(alarm), mon, at(mon, 7), zone, boundary))
        // Отмечено сегодня — следующее только завтра.
        val done = alarm.copy(habit = alarm.habit.copy(doneDays = setOf(mon)))
        assertEquals(emptyList<Reminder>(), Reminders.habitsDue(listOf(done), mon, at(mon, 7), at(mon, 8), zone, boundary))
        assertEquals(at(mon + 1, 7, 30), Reminders.nextHabit(listOf(done), mon, at(mon, 7), zone, boundary))
    }

    @Test
    fun `не в свои дни недели не напоминает`() {
        val rule = RepeatRule.Weekly(setOf(DayOfWeek.WEDNESDAY))
        val alarm = HabitAlarm(HabitInfo("h", rule, mon - 10, emptySet()), "Бассейн", 19 * 60)
        assertNull(Reminders.nextHabit(listOf(alarm), mon, at(mon, 7), zone, boundary))
        assertEquals(at(mon + 2, 19), Reminders.nextHabit(listOf(alarm), mon + 1, at(mon + 1, 20), zone, boundary))
    }

    @Test
    fun `время после полуночи относится к той же логической ночи`() {
        val alarm = HabitAlarm(HabitInfo("h", null, mon - 10, emptySet()), "Спать", 60)
        val expected = LocalDateTime.of(2026, 11, 3, 1, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, Reminders.nextHabit(listOf(alarm), mon, at(mon, 23), zone, boundary))
    }
}
