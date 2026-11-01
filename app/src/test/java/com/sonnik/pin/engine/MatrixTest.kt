package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixTest {
    private val today = 20_000L

    private fun item(id: String, day: Long?, high: Boolean = false, type: EntryType = EntryType.ONE_TIME, rollovers: Int = 0) =
        MatrixItem(id, type, day, if (high) Priority.HIGH else Priority.NORMAL, rollovers)

    @Test
    fun `срочность — сегодня, завтра и просроченное`() {
        assertEquals(Quadrant.DO, Matrix.quadrant(item("a", today, high = true), today))
        assertEquals(Quadrant.DO, Matrix.quadrant(item("a", today - 3, high = true), today))
        assertEquals(Quadrant.QUICK, Matrix.quadrant(item("a", today + 1), today))
        assertEquals(Quadrant.SCHEDULE, Matrix.quadrant(item("a", today + 2, high = true), today))
        assertEquals(Quadrant.LATER, Matrix.quadrant(item("a", today + 10), today))
        assertEquals(Quadrant.SCHEDULE, Matrix.quadrant(item("plan", null, high = true, type = EntryType.PLAN), today))
    }

    @Test
    fun `дальние повторы не показываются, порядок по дню и переносам`() {
        val board = Matrix.build(
            listOf(
                item("far", today + 5, type = EntryType.RECURRING),
                item("near", today, type = EntryType.RECURRING),
                item("b", today),
                item("a", today, rollovers = 2),
                item("old", today - 1),
            ),
            today,
        )
        assertEquals(listOf("old", "a", "b", "near"), board.getValue(Quadrant.QUICK).map { it.id })
        assertEquals(emptyList<MatrixItem>(), board.getValue(Quadrant.LATER))
        assertEquals(Quadrant.entries.toSet(), board.keys)
    }
}
