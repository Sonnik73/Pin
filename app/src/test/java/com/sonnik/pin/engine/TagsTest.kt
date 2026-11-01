package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {
    private val all = listOf("работа", "дом", "дача", "машина", "подработка", "Ремонт")

    @Test
    fun `подсказки по последнему слову`() {
        assertEquals(listOf("работа", "подработка"), Tags.suggest(all, "раб"))
        assertEquals(listOf("дача", "дом", "подработка"), Tags.suggest(all, "работа, д"))
        assertEquals(listOf("дача"), Tags.suggest(all, "дом, да"))
        assertEquals(listOf("ремонт"), Tags.suggest(all, "#РЕМ"))
        assertEquals(emptyList<String>(), Tags.suggest(all, "дом, "))
        assertEquals(emptyList<String>(), Tags.suggest(all, "дом"))
    }

    @Test
    fun `подстановка`() {
        assertEquals("работа, ", Tags.apply("раб", "работа"))
        assertEquals("дом, дача, ", Tags.apply("дом, да", "дача"))
    }

    @Test
    fun `категории людей`() {
        assertEquals("HUSBAND", Relation.key("Муж"))
        assertEquals("WIFE", Relation.key("WIFE"))
        assertEquals("крёстный", Relation.key("  Крёстный "))
        assertEquals("OTHER", Relation.key(""))
        assertEquals("муж", Relation.title("HUSBAND"))
        assertEquals("крёстный", Relation.title("крёстный"))
        assertEquals(listOf("крёстный", "тренер"), Relation.customOf(listOf("WIFE", "тренер", "крёстный", "тренер", null)))
    }

    @Test
    fun `точки в дате`() {
        assertEquals("", RuText.dateFromDigits(""))
        assertEquals("05", RuText.dateFromDigits("05"))
        assertEquals("05.0", RuText.dateFromDigits("050"))
        assertEquals("05.03", RuText.dateFromDigits("0503"))
        assertEquals("05.03.19", RuText.dateFromDigits("050319"))
        assertEquals("05.03.1996", RuText.dateFromDigits("05.03.1996"))
        assertEquals("05.03.1996", RuText.dateFromDigits("0503199612"))
    }
}
