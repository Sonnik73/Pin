package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun `версии сравниваются числами`() {
        assertTrue(Updates.isNewer("v0.35", "0.34"))
        assertTrue(Updates.isNewer("v0.10", "0.9"))
        assertTrue(Updates.isNewer("1.0", "0.99"))
        assertFalse(Updates.isNewer("v0.34", "0.34"))
        assertFalse(Updates.isNewer("v0.33", "0.34"))
        assertFalse(Updates.isNewer("ночная", "0.34"))
        assertEquals(listOf(1, 2, 3), Updates.parts("v1.2.3-beta"))
    }

    @Test
    fun `пропущенную версию не предлагаем, следующую — да`() {
        val r = ReleaseInfo("v0.35", "https://github.com/x", null, "")
        assertTrue(Updates.shouldOffer(r, "0.34", null))
        assertFalse(Updates.shouldOffer(r, "0.34", "0.35"))
        assertTrue(Updates.shouldOffer(r.copy(version = "v0.36"), "0.34", "0.35"))
        assertFalse(Updates.shouldOffer(null, "0.34", null))
    }

    @Test
    fun `проверка не чаще раза в 6 часов`() {
        val h = 60 * 60 * 1000L
        assertTrue(Updates.isDue(0, 7 * h))
        assertFalse(Updates.isDue(5 * h, 7 * h))
        assertTrue(Updates.isDue(10 * h, 7 * h)) // часы перевели назад
    }

    @Test
    fun `заголовок выпуска убирается из текста`() {
        assertEquals("- Новое\n- Ещё", Updates.cleanNotes("## 0.35\n\n- Новое\n- Ещё\n\n"))
    }
}
