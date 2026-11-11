package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun `поиск по нескольким полям — каждое слово где-то есть`() {
        val fields = listOf("Кристина", "жена", "любит пионы", "Наушники")
        assertTrue(Search.matchesAll("крис", fields))
        assertTrue(Search.matchesAll("жена  наушн", fields))
        assertTrue(Search.matchesAll("", fields))
        assertFalse(Search.matchesAll("крис друг", fields))
        assertTrue(Search.matchesAll("Семён", listOf("семен")))
    }

    private val today = 100L
    private val items = listOf(
        SearchItem("1", EntryType.ONE_TIME, "Купить хлеб", "бородинский", listOf("дом"), dueDay = 101),
        SearchItem("2", EntryType.ONE_TIME, "Отчёт по серверам", "собрать логи", listOf("работа"), Priority.HIGH, dueDay = 100),
        SearchItem("3", EntryType.PLAN, "Съездить в Казань", tags = listOf("путешествия")),
        SearchItem("4", EntryType.GIFT, "Наушники", extra = "Аня день рождения"),
        SearchItem("5", EntryType.ONE_TIME, "Старый отчёт", status = TaskStatus.DONE, dueDay = 90),
        SearchItem("6", EntryType.ONE_TIME, "Хвост", dueDay = 99),
        SearchItem("7", EntryType.HOLIDAY, "Новый год", dueDay = 50),
    )

    private fun ids(f: SearchFilter, rollovers: Map<String, Int> = emptyMap()) =
        Search.filter(items, f, today) { rollovers[it] ?: 0 }.map { it.id }

    @Test
    fun `поиск по словам, ё и регистру`() {
        assertEquals(listOf("2", "5"), ids(SearchFilter(query = "ОТЧЕТ")))
        assertEquals(listOf("2"), ids(SearchFilter(query = "отчёт логи")))
        assertEquals(listOf("1"), ids(SearchFilter(query = "бородин")))
        assertEquals(listOf("4"), ids(SearchFilter(query = "аня")))
        assertEquals(listOf("3"), ids(SearchFilter(query = "путешеств")))
        assertTrue(ids(SearchFilter(query = "нет такого")).isEmpty())
    }

    @Test
    fun `в названии выше, чем в описании`() {
        val list = listOf(
            SearchItem("a", EntryType.ONE_TIME, "Позвонить", "про хлеб", dueDay = 100),
            SearchItem("b", EntryType.ONE_TIME, "Хлеб", dueDay = 105),
        )
        assertEquals(listOf("b", "a"), Search.filter(list, SearchFilter(query = "хлеб"), today).map { it.id })
    }

    @Test
    fun `фильтры`() {
        assertEquals(listOf("3"), ids(SearchFilter(types = setOf(EntryType.PLAN))))
        assertEquals(listOf("2"), ids(SearchFilter(tag = "Работа")))
        assertEquals(listOf("2"), ids(SearchFilter(importantOnly = true)))
        assertEquals(listOf("5"), ids(SearchFilter(state = SearchState.DONE)))
        assertEquals(listOf("4", "3"), ids(SearchFilter(state = SearchState.NO_DATE)))
        assertEquals(listOf("6"), ids(SearchFilter(state = SearchState.OVERDUE)))
        assertEquals(listOf("2", "6"), ids(SearchFilter(state = SearchState.OVERDUE), mapOf("2" to 1)).sorted())
        assertTrue(SearchFilter().isEmpty)
    }

    @Test
    fun `архив — сверху выполненное последним`() {
        val done = { id: String, day: Long?, at: Long ->
            SearchItem(id, EntryType.ONE_TIME, "Дело $id", status = TaskStatus.DONE, dueDay = 100, doneDay = day, updatedAt = at)
        }
        val items = listOf(done("old", 10, 1), done("new", 30, 5), done("mid-early", 20, 2), done("mid-late", 20, 9), done("unknown", null, 0))
        val archive = Search.filter(items, SearchFilter(state = SearchState.DONE), today = 40)
        assertEquals(listOf("new", "mid-late", "mid-early", "old", "unknown"), archive.map { it.id })
        // Открытые по-прежнему — ближние сверху и раньше выполненных.
        val open = SearchItem("o2", EntryType.ONE_TIME, "Открыто 2", dueDay = 50)
        val open1 = SearchItem("o1", EntryType.ONE_TIME, "Открыто 1", dueDay = 45)
        assertEquals(listOf("o1", "o2", "new"), Search.filter(listOf(done("new", 30, 5), open, open1), SearchFilter(), today = 40).map { it.id })
    }
}
