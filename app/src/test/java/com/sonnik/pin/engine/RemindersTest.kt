package com.sonnik.pin.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val day = LocalDate.of(2026, 11, 2).toEpochDay()
    private val boundary = 240

    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun task(id: String, time: Int?, status: TaskStatus = TaskStatus.OPEN, dueDay: Long = day) =
        TaskSnapshot(id, EntryType.ONE_TIME, status, dueDay, dueDay, time, title = id)

    @Test
    fun `момент напоминания, ночное время — следующая дата`() {
        assertEquals(at(2, 18, 30), Reminders.reminderAt(task("a", 18 * 60 + 30), zone, boundary))
        assertEquals(at(3, 1, 0), Reminders.reminderAt(task("night", 60), zone, boundary))
        assertNull(Reminders.reminderAt(task("none", null), zone, boundary))
    }

    @Test
    fun `наступившие напоминания показываются один раз`() {
        val tasks = listOf(task("a", 9 * 60), task("b", 12 * 60), task("done", 10 * 60, TaskStatus.DONE), task("c", 20 * 60))
        val first = Reminders.due(tasks, after = at(2, 8), now = at(2, 12), zone, boundary)
        assertEquals(listOf("a", "b"), first.map { it.taskId })
        assertTrue(Reminders.due(tasks, after = at(2, 12), now = at(2, 13), zone, boundary).isEmpty())
        assertEquals(at(2, 20), Reminders.nextReminder(tasks, at(2, 12), zone, boundary))
    }

    @Test
    fun `утренняя сводка`() {
        val morning = 8 * 60
        assertEquals(at(2, 8), Reminders.nextMorning(day, day - 1, at(2, 6), morning, zone, boundary))
        assertEquals(at(3, 8), Reminders.nextMorning(day, day, at(2, 6), morning, zone, boundary))
        assertEquals(at(3, 8), Reminders.nextMorning(day, day - 1, at(2, 9), morning, zone, boundary))
        assertTrue(Reminders.morningDue(day, day - 1, at(2, 9), morning, zone, boundary))
        assertFalse(Reminders.morningDue(day, day, at(2, 9), morning, zone, boundary))
        assertFalse(Reminders.morningDue(day, day - 1, at(2, 7), morning, zone, boundary))
    }

    @Test
    fun `текст сводки`() {
        val board = TodayBoard.build(
            listOf(
                task("Позвонить", 9 * 60),
                TaskSnapshot("r", EntryType.ONE_TIME, TaskStatus.OPEN, day - 1, day, null, Priority.HIGH, 1, title = "Отчёт"),
            ),
            day,
            doneDayOf = { null },
        )
        val s = MorningSummary.of(board)!!
        assertEquals("Сегодня 2 задачи", s.title)
        assertEquals("Перенесено: 1\nОтчёт\n09:00 Позвонить", s.text)
        assertNull(MorningSummary.of(TodayBoard.build(emptyList(), day, { null })))
    }
}
