package com.sonnik.pin.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringRolloverTest {
    // Понедельник 2 ноября 2026.
    private val monday = LocalDate.of(2026, 11, 2).toEpochDay()

    private fun task(occurrence: Long, current: Long = occurrence, rollovers: Int = 0) =
        TaskSnapshot("r", EntryType.RECURRING, TaskStatus.OPEN, occurrence, current, rollovers = rollovers)

    @Test
    fun `ежедневная — вчерашний повтор пропущен, сегодня новый`() {
        val schedule = Schedule(RepeatRule.Daily, monday)
        val result = Rollover.apply(task(monday), monday + 1, schedule)!!
        assertEquals(monday + 1, result.task.originalDay)
        assertEquals(monday + 1, result.task.currentDay)
        assertEquals(0, result.task.rollovers)
        assertTrue(result.events.isEmpty())
        assertEquals(listOf(MissedEvent("r", monday, monday + 1, 0)), result.missed)
    }

    @Test
    fun `еженедельная переносится до следующего повтора`() {
        val schedule = Schedule(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)), monday)
        val wednesday = Rollover.apply(task(monday), monday + 2, schedule)!!
        assertEquals(monday, wednesday.task.originalDay)
        assertEquals(monday + 2, wednesday.task.currentDay)
        assertEquals(2, wednesday.task.rollovers)
        assertTrue(wednesday.missed.isEmpty())

        // Наступил следующий понедельник: старый повтор пропущен, без дубля.
        val nextMonday = Rollover.apply(wednesday.task, monday + 7, schedule)!!
        assertEquals(monday + 7, nextMonday.task.originalDay)
        assertEquals(monday + 7, nextMonday.task.currentDay)
        assertEquals(0, nextMonday.task.rollovers)
        assertEquals(listOf(MissedEvent("r", monday, monday + 7, 6)), nextMonday.missed)
        assertEquals((monday + 2 until monday + 6).toList(), nextMonday.events.map { it.fromDay })
    }

    @Test
    fun `долгое отсутствие досчитывается так же, как по дням`() {
        val schedule = Schedule(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), monday)
        val atOnce = Rollover.apply(task(monday), monday + 20, schedule)!!
        var t = task(monday)
        val missed = ArrayList<MissedEvent>()
        var events = 0
        for (day in monday + 1..monday + 20) {
            Rollover.apply(t, day, schedule)?.let {
                t = it.task
                missed += it.missed
                events += it.events.size
            }
        }
        assertEquals(atOnce.task, t)
        assertEquals(atOnce.missed, missed)
        assertEquals(atOnce.events.size, events)
        // Сегодня вс 22 ноября: пропущены пн 2, чт 5, пн 9, чт 12, пн 16; четверговый от 19-го ещё висит.
        assertEquals(5, atOnce.missed.size)
        assertEquals(monday + 17, atOnce.task.originalDay)
        assertEquals(3, atOnce.task.rollovers)
    }

    @Test
    fun `без правила периодическая не переносится`() {
        assertNull(Rollover.apply(task(monday), monday + 1, null))
    }

    @Test
    fun `следующий повтор после выполнения`() {
        val schedule = Schedule(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)), monday)
        // Понедельничный выполнен во вторник.
        val late = Rollover.nextOccurrence(task(monday, monday + 1, 1), monday + 1, schedule)
        assertEquals(monday + 7, late.originalDay)
        assertEquals(0, late.rollovers)
        // Выполнен заранее, в субботу до него.
        val early = Rollover.nextOccurrence(task(monday + 7), monday + 5, schedule)
        assertEquals(monday + 14, early.currentDay)
        // Ежедневная, выполнена в свой день.
        val daily = Rollover.nextOccurrence(task(monday), monday, Schedule(RepeatRule.Daily, monday))
        assertEquals(monday + 1, daily.currentDay)
    }

    @Test
    fun `пропуск повтора из разбора дня`() {
        val schedule = Schedule(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), monday)
        // Понедельничный висит со среды; пропускаем — следующий четверг.
        val (next, missed) = Rollover.skipOccurrence(task(monday, monday + 2, 2), monday + 2, schedule)
        assertEquals(monday + 3, next.currentDay)
        assertEquals(0, next.rollovers)
        assertEquals(MissedEvent("r", monday, monday + 2, 2), missed)
        // Ежедневная, висящая с позавчера: следующий — не раньше сегодня.
        val daily = Schedule(RepeatRule.Daily, monday)
        assertEquals(monday + 5, Rollover.skipOccurrence(task(monday, monday + 5, 5), monday + 5, daily).first.currentDay)
    }
}
