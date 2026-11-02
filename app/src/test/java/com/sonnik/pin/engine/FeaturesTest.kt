package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FeaturesTest {
    @Test
    fun `выключенные функции сохраняются и читаются, неизвестные пропускаются`() {
        val off = setOf(Feature.PLANS, Feature.MATRIX)
        assertEquals("MATRIX,PLANS", Feature.formatDisabled(off))
        assertEquals(off, Feature.parseDisabled("MATRIX, PLANS,UNKNOWN"))
        assertEquals(emptySet<Feature>(), Feature.parseDisabled(null))
        assertEquals(setOf(Feature.SWIPE_DONE), Feature.parseDisabled("SWIPE_DONE"))
        assertEquals(setOf(Feature.SWIPE_DONE, Feature.SWIPE_TOMORROW), Feature.parseDisabled("SWIPE_TOMORROW,SWIPE_DONE"))
    }
}
