package com.sonnik.pin.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RuTextTest {
    private val today = LocalDate.of(2026, 11, 1).toEpochDay()

    @Test
    fun `склонение переносов`() {
        assertEquals("перенесено 1 раз", RuText.rollovers(1))
        assertEquals("перенесено 3 раза", RuText.rollovers(3))
        assertEquals("перенесено 5 раз", RuText.rollovers(5))
        assertEquals("перенесено 11 раз", RuText.rollovers(11))
        assertEquals("перенесено 22 раза", RuText.rollovers(22))
        assertEquals("перенесено 21 раз", RuText.rollovers(21))
    }

    @Test
    fun `относительные дни`() {
        assertEquals("сегодня", RuText.relativeDay(today, today))
        assertEquals("завтра", RuText.relativeDay(today + 1, today))
        assertEquals("вчера", RuText.relativeDay(today - 1, today))
        assertEquals("пт, 6 ноября", RuText.relativeDay(today + 5, today))
        assertEquals("пт, 1 января 2027", RuText.relativeDay(LocalDate.of(2027, 1, 1).toEpochDay(), today))
    }

    @Test
    fun `заголовок и время`() {
        assertEquals("Воскресенье, 1 ноября", RuText.headline(today))
        assertEquals("09:05", RuText.time(9 * 60 + 5))
    }
}
