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

    @Test
    fun `значки на акценте — тёмные на светлом акценте, белые на тёмном`() {
        // «Киберпанк»: неоново-жёлтый акцент — «+» и кружок «сегодня» на нём тёмные.
        val cyber = WidgetColors.of(0xFFFCEE0A.toInt(), 2, 3, 1f, dark = true)
        assertEquals(0xFF1F2328.toInt(), cyber.onAccent)
        // Светлые акценты тёмных тем («Булавка» — светло-красный) — тоже тёмные значки: белый на них бледнел.
        val pinDark = WidgetColors.of(0xFFFF8A7F.toInt(), 2, 3, 1f, dark = true)
        assertEquals(0xFF1F2328.toInt(), pinDark.onAccent)
        // Тёмные акценты («Булавка» светлая) — значки белые, как раньше.
        val pinLight = WidgetColors.of(0xFFC8423A.toInt(), 2, 3, 1f, dark = false)
        assertEquals(0xFFFFFFFF.toInt(), pinLight.onAccent)
    }
}
