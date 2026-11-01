package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthMarksTest {
    @Test
    fun `значки дня как в календаре`() {
        val marks = MonthMarks.build(
            listOf(
                MarkItem(1, MarkKind.OPEN, EntryType.ONE_TIME, important = true),
                MarkItem(1, MarkKind.PROJECTED, EntryType.RECURRING),
                MarkItem(1, MarkKind.DONE, EntryType.ONE_TIME),
                MarkItem(1, MarkKind.DONE, EntryType.HABIT),
                MarkItem(1, MarkKind.OPEN, EntryType.BIRTHDAY),
                MarkItem(2, MarkKind.PROJECTED, EntryType.HOLIDAY),
                MarkItem(3, MarkKind.DONE, EntryType.ONE_TIME, important = true),
            ),
        )
        assertEquals(DayMarks(listOf(MarkKind.OPEN, MarkKind.PROJECTED), important = true, birthday = true, holiday = false), marks[1])
        assertEquals(DayMarks(emptyList(), important = false, birthday = false, holiday = true), marks[2])
        assertEquals(DayMarks(listOf(MarkKind.DONE), important = false, birthday = false, holiday = false), marks[3])
    }
}
