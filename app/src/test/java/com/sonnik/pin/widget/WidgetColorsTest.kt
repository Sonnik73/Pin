package com.sonnik.pin.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorsTest {
    @Test
    fun `смешивание цветов и фон с оттенком палитры`() {
        assertEquals(0xFF000000.toInt(), WidgetColors.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFF7F7F7F.toInt(), WidgetColors.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        val light = WidgetColors.of(0xFF56682F.toInt(), 0xFF8C5A2B.toInt(), 0xFFA68A4E.toInt(), 3f, dark = false)
        // Фон «Гарнизона» — пергамент, заметно теплее базового.
        assertEquals(false, light.background == 0xFFFBF8F1.toInt())
        assertEquals(0xFF56682F.toInt(), light.accent)
        val dark = WidgetColors.of(1, 2, 3, 1f, dark = true)
        assertEquals(0xFFE6E2DA.toInt(), dark.text)
    }
}
