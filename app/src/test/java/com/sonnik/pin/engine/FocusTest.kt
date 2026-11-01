package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusTest {
    private val min = 60_000L

    @Test
    fun `работа — перерыв — стоп`() {
        val work = Focus.start(0, "t")
        assertEquals(25, work.minutes)
        val afterWork = Focus.finish(work, 25 * min)
        assertEquals(25, afterWork.workedMinutes)
        val brk = afterWork.next!!
        assertEquals(FocusPhase.BREAK, brk.phase)
        assertEquals(30 * min, brk.endsAt)
        assertEquals(1, brk.round)
        assertEquals("t", brk.taskId)
        val afterBreak = Focus.finish(brk, 30 * min)
        assertNull(afterBreak.next)
        assertEquals(0, afterBreak.workedMinutes)
    }

    @Test
    fun `каждый четвёртый — длинный перерыв`() {
        val step = Focus.finish(Focus.start(0, null, round = 3), 25 * min)
        assertEquals(FocusPhase.LONG_BREAK, step.next!!.phase)
        assertEquals(15, step.next!!.minutes)
    }

    @Test
    fun `досрочная остановка и формат`() {
        val step = Focus.finish(Focus.start(0, null), 12 * min + 30_000)
        assertNull(step.next)
        assertEquals(12, step.workedMinutes)
        // Будильник опоздал — минуты не больше длины отрезка.
        assertEquals(25, Focus.finish(Focus.start(0, null), 40 * min).workedMinutes)
        assertEquals("25:00", Focus.format(25 * min))
        assertEquals("00:01", Focus.format(1))
        assertEquals("04:59", Focus.format(4 * min + 59_000))
    }
}
