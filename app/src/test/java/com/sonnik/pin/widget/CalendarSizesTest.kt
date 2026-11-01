package com.sonnik.pin.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSizesTest {
    @Test
    fun `шрифт растёт с размером виджета и не выходит за пределы`() {
        val small = CalendarSizes.of(200, 200, 6)
        val big = CalendarSizes.of(380, 330, 5)
        assertEquals(11f, small.day, 0.01f)
        assertTrue(big.day > 17f)
        assertTrue(big.circle + big.marks * 1.4f <= (330 - 80f) / 5 + 0.01f)
        assertEquals(26f, CalendarSizes.of(1000, 1000, 4).day, 0.01f)
    }
}
