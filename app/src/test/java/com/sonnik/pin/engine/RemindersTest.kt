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

    @Test
    fun `несколько напоминаний у одной задачи`() {
        // Задача завтра в 18:00: за день, за 2 часа и в срок.
        val t = task("a", 18 * 60, dueDay = day + 1).copy(reminders = listOf(120, 24 * 60, 0))
        val fired = Reminders.due(listOf(t), after = at(2, 0), now = at(3, 23), zone, boundary)
        assertEquals(listOf(at(2, 18), at(3, 16), at(3, 18)), fired.map { it.at })
        assertEquals(listOf(24 * 60, 120, 0), fired.map { it.before })
        // Каждое — ровно один раз: окно после первого его уже не содержит.
        assertEquals(listOf(at(3, 16)), Reminders.due(listOf(t), after = at(2, 18), now = at(3, 17), zone, boundary).map { it.at })
        assertEquals(at(3, 16), Reminders.nextReminder(listOf(t), at(2, 19), zone, boundary))
        // Без напоминаний — молчит.
        assertTrue(Reminders.due(listOf(t.copy(reminders = emptyList())), at(2, 0), at(3, 23), zone, boundary).isEmpty())
        // Отложили напоминание «за день» до 20:00 — остальные (позже) на месте.
        val snoozed = t.copy(snoozeUntil = at(2, 20))
        assertEquals(listOf(at(2, 20), at(3, 16), at(3, 18)), Reminders.moments(snoozed, zone, boundary).map { it.first }.sorted())
    }

    @Test
    fun `напоминания в поле записи и текст уведомления`() {
        assertEquals(listOf(15), Reminders.parse(null, 15))
        assertEquals(emptyList<Int>(), Reminders.parse("", 15))
        assertEquals(listOf(0, 120, 1440), Reminders.parse("1440, 120,0,120", 0))
        assertEquals("0,5,15,30,60", Reminders.format(listOf(60, 30, 15, 5, 0, 120)))
        assertEquals(Reminders.MAX_ADVANCE, Reminders.parse("999999", 0).single())
        assertEquals(120, Reminders.suggestNext(listOf(0, 60)))
        assertEquals("В 18:00 — через 2 часа", Reminders.noticeText(120, 18 * 60))
        assertEquals("В 18:00 — через день", Reminders.noticeText(24 * 60, 18 * 60))
        assertEquals("Пора: на 18:00", Reminders.noticeText(0, 18 * 60))
        assertNull(Reminders.noticeText(null, 18 * 60))
    }

    @Test
    fun `заранее и отложенные`() {
        val early = task("a", 18 * 60).copy(reminders = listOf(15))
        assertEquals(at(2, 17, 45), Reminders.reminderAt(early, zone, boundary))
        // Отложили на 20:00 — напоминание сдвигается; после переноса на завтра снова время задачи.
        val snoozed = task("b", 18 * 60).copy(snoozeUntil = at(2, 20))
        assertEquals(at(2, 20), Reminders.reminderAt(snoozed, zone, boundary))
        assertEquals(at(3, 18), Reminders.reminderAt(snoozed.copy(currentDay = day + 1), zone, boundary))
        // Задача без времени, отложенная из уведомления.
        assertEquals(at(2, 13), Reminders.reminderAt(task("c", null).copy(snoozeUntil = at(2, 13)), zone, boundary))
        assertEquals("за 15 минут", Reminders.describeAdvance(15))
        assertEquals("за час", Reminders.describeAdvance(60))
        assertEquals("в срок", Reminders.describeAdvance(0))
        assertEquals("за 45 минут", Reminders.describeAdvance(45))
        assertEquals("за 2 часа", Reminders.describeAdvance(120))
        assertEquals("за 2 ч 30 мин", Reminders.describeAdvance(150))
        assertEquals("за день", Reminders.describeAdvance(24 * 60))
        assertEquals("за 3 дня", Reminders.describeAdvance(3 * 24 * 60))
        assertEquals("за 1 дн 2 ч", Reminders.describeAdvance(26 * 60))
    }
}
