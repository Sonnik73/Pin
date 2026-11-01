package com.sonnik.pin.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSizesTest {
    @Test
    fun `шрифт растёт с размером виджета и не выходит за пределы`() {
        val small = CalendarSizes.of(200, 200, 6)
        val big = CalendarSizes.of(380, 330, 5)
        assertEquals(12f, small.day, 0.01f)
        assertTrue(big.day > 17f)
        assertTrue(big.day <= 26f && big.circle <= (330 - 80f) / 5)
        assertEquals(26f, CalendarSizes.of(1000, 1000, 4).day, 0.01f)
    }
}
