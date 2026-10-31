package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolloverTest {
    private fun task(
        id: String = "a",
        day: Long = 100,
        status: TaskStatus = TaskStatus.OPEN,
        type: EntryType = EntryType.ONE_TIME,
        rollovers: Int = 0,
        original: Long = day,
    ) = TaskSnapshot(id = id, type = type, status = status, originalDay = original, currentDay = day, rollovers = rollovers)

    @Test
    fun `невыполненная задача переезжает на следующий день`() {
        val result = Rollover.apply(task(day = 100), today = 101)!!
        assertEquals(101, result.task.currentDay)
        assertEquals(100, result.task.originalDay)
        assertEquals(1, result.task.rollovers)
        assertEquals(listOf(RolloverEvent("a", 100)), result.events)
    }

    @Test
    fun `пропущенные дни досчитываются по одной ночи`() {
        val result = Rollover.apply(task(day = 100, rollovers = 2, original = 98), today = 104)!!
        assertEquals(104, result.task.currentDay)
        assertEquals(98, result.task.originalDay)
        assertEquals(6, result.task.rollovers)
        assertEquals(listOf(100L, 101L, 102L, 103L), result.events.map { it.fromDay })
        assertEquals(listOf(101L, 102L, 103L, 104L), result.events.map { it.toDay })
    }

    @Test
    fun `повторный перенос в тот же день ничего не меняет`() {
        val once = Rollover.apply(task(day = 100), today = 103)!!.task
        assertNull(Rollover.apply(once, today = 103))
    }

    @Test
    fun `досчёт по дням равен переносу за раз`() {
        var t = task(day = 100)
        for (d in 101L..105L) t = Rollover.apply(t, d)!!.task
        assertEquals(Rollover.apply(task(day = 100), 105)!!.task, t)
    }

    @Test
    fun `выполненные, сегодняшние и будущие задачи не переносятся`() {
        assertNull(Rollover.apply(task(day = 100, status = TaskStatus.DONE), today = 101))
        assertNull(Rollover.apply(task(day = 100, status = TaskStatus.MISSED), today = 101))
        assertNull(Rollover.apply(task(day = 101), today = 101))
        assertNull(Rollover.apply(task(day = 105), today = 101))
    }

    @Test
    fun `планы без даты и периодические не переносятся этим правилом`() {
        assertNull(Rollover.apply(task(type = EntryType.PLAN), today = 101))
        assertNull(Rollover.apply(task(type = EntryType.RECURRING), today = 101))
    }

    @Test
    fun `applyAll возвращает только перенесённые`() {
        val all = listOf(task("a", 99), task("b", 101), task("c", 100, TaskStatus.DONE), task("d", 100))
        val results = Rollover.applyAll(all, today = 101)
        assertEquals(listOf("a", "d"), results.map { it.task.id })
        assertTrue(results.all { it.task.currentDay == 101L })
        assertEquals(3, results.sumOf { it.events.size })
    }
}
