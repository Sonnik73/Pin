package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListsTest {
    private val lists = listOf(TaskListRef("1", "Дом"), TaskListRef("2", "Работа"), TaskListRef("3", "Ремонт"))

    @Test
    fun `сопоставление по имени`() {
        assertEquals("1", Lists.match(lists, "дом")?.id)
        assertEquals("2", Lists.match(lists, "раб")?.id)
        assertNull(Lists.match(lists, "р")) // два кандидата
        assertNull(Lists.match(lists, "дача"))
        assertNull(Lists.match(lists, null))
    }

    @Test
    fun `цвета`() {
        assertEquals(ListColor.GREEN, ListColor.parse("green"))
        assertEquals(ListColor.BLUE, ListColor.parse("нет такого"))
        assertEquals(ListColor.ORANGE, ListColor.next(listOf("RED")))
    }
}
