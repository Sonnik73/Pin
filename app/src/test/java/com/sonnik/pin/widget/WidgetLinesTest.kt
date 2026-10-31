package com.sonnik.pin.widget

import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus
import com.sonnik.pin.engine.TodayBoard
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLinesTest {
    @Test
    fun `перенесённые сверху, лишнее — в счётчик`() {
        val today = 100L
        val tasks = listOf(
            TaskSnapshot("a", EntryType.ONE_TIME, TaskStatus.OPEN, 100, 100, 9 * 60, title = "Утро"),
            TaskSnapshot("b", EntryType.ONE_TIME, TaskStatus.OPEN, 98, 100, rollovers = 2, title = "Хвост"),
            TaskSnapshot("c", EntryType.ONE_TIME, TaskStatus.OPEN, 100, 100, title = "Без времени"),
            TaskSnapshot("d", EntryType.ONE_TIME, TaskStatus.DONE, 100, 100, title = "Сделано"),
        )
        val (lines, more) = widgetLines(TodayBoard.build(tasks, today, { today }), limit = 2)
        assertEquals(listOf(WidgetLine("b", "Хвост", "↷2", true), WidgetLine("a", "Утро", "09:00", false)), lines)
        assertEquals(1, more)
    }
}
