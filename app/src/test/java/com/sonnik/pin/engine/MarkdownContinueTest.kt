package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownContinueTest {
    /** Нажать Enter в позиции [at] текста [text]. */
    private fun enter(text: String, at: Int = text.length): Pair<String, Int>? =
        Markdown.continueList(text, text.substring(0, at) + "\n" + text.substring(at), at + 1)

    @Test
    fun `чекбокс продолжается пустым чекбоксом`() {
        assertEquals("- [x] хлеб\n- [ ] " to 17, enter("- [x] хлеб"))
    }

    @Test
    fun `маркированный и нумерованный список с отступом`() {
        assertEquals("  * сыр\n  * " to 12, enter("  * сыр"))
        assertEquals("1. раз\n2. " to 10, enter("1. раз"))
        assertEquals("9) девять\n10) " to 14, enter("9) девять"))
    }

    @Test
    fun `пустой пункт заканчивает список`() {
        assertEquals("- хлеб\n" to 7, enter("- хлеб\n- "))
    }

    @Test
    fun `обычный текст и не одиночный перенос — без изменений`() {
        assertNull(enter("просто строка"))
        assertNull(Markdown.continueList("- а", "- а\n\n", 5))
    }

    @Test
    fun `перенос в середине списка`() {
        val text = "- а\n- б"
        assertEquals("- а\n- \n- б" to 6, enter(text, 3))
    }
}
