package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayBoardTest {
    private val today = 200L

    private fun task(
        id: String,
        day: Long = today,
        priority: Priority = Priority.NORMAL,
        time: Int? = null,
        rollovers: Int = 0,
        status: TaskStatus = TaskStatus.OPEN,
        created: Long = 0,
    ) = TaskSnapshot(id, EntryType.ONE_TIME, status, day - rollovers, day, time, priority, rollovers, created)

    @Test
    fun `перенесённые сверху, самые зависшие первыми`() {
        val board = TodayBoard.build(
            listOf(task("new"), task("r1", rollovers = 1), task("r5", rollovers = 5)),
            today,
            doneDayOf = { null },
        )
        assertEquals(listOf("r5", "r1"), board.rolledOver.map { it.id })
        assertEquals(listOf("new"), board.today.map { it.id })
        assertEquals(3, board.openCount)
    }

    @Test
    fun `важные выше, затем по времени, без времени в конце`() {
        val board = TodayBoard.build(
            listOf(
                task("noTime", created = 1),
                task("late", time = 18 * 60),
                task("early", time = 9 * 60),
                task("important", priority = Priority.HIGH, created = 5),
                task("low", priority = Priority.LOW, time = 8 * 60),
            ),
            today,
            doneDayOf = { null },
        )
        assertEquals(listOf("important", "early", "late", "noTime", "low"), board.today.map { it.id })
    }

    @Test
    fun `выполненные сегодня и ближайшие дни`() {
        val board = TodayBoard.build(
            listOf(
                task("doneToday", status = TaskStatus.DONE),
                task("doneEarlier", day = today - 3, status = TaskStatus.DONE),
                task("tomorrow", day = today + 1),
                task("week", day = today + 7),
                task("far", day = today + 30),
            ),
            today,
            doneDayOf = { if (it.id == "doneToday") today else today - 3 },
        )
        assertEquals(listOf("doneToday"), board.done.map { it.id })
        assertEquals(listOf("tomorrow", "week"), board.upcoming.map { it.id })
    }

    @Test
    fun `задача из прошлого без переноса тоже считается перенесённой`() {
        val board = TodayBoard.build(listOf(task("stale", day = today - 2)), today, doneDayOf = { null })
        assertEquals(listOf("stale"), board.rolledOver.map { it.id })
    }

    @Test
    fun `вовремя — не позже исходного дня`() {
        val t = task("x", rollovers = 2)
        assertFalse(Completion.isOnTime(t, today))
        assertTrue(Completion.isOnTime(t, today - 2))
        assertTrue(Completion.isOnTime(task("y"), today - 1))
    }
}
