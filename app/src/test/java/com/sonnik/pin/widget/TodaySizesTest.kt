package com.sonnik.pin.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySizesTest {
    @Test
    fun `шрифт и число строк по размеру виджета`() {
        val small = TodaySizes.of(180, 110, hasEvent = false)
        assertEquals(14f, small.text, 0.01f)
        assertEquals(1, small.rows)
        val big = TodaySizes.of(380, 380, hasEvent = true)
        assertTrue(big.text > 18f)
        assertTrue(big.rows in 4..8)
        assertTrue(TodaySizes.of(380, 600, hasEvent = false).rows > big.rows)
    }
}
