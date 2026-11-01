package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BirthdayTextTest {
    @Test
    fun `разные записи строк`() {
        val text = """
            # Дни рождения семьи
            Аня 10.11.1997
            Мама — 04.03
            10/11/1980 Серёга Иванов
            Вася: 5 марта 1990
            Петя 1.2.85
            Лена 31.02.2000
            просто строка
            Тётя Света, 21 июля
            Ваня 7 мая
        """.trimIndent()
        val r = BirthdayText.parse(text, currentYear = 2026)
        assertEquals(
            listOf(
                SharedPerson("Аня", month = 11, day = 10, year = 1997),
                SharedPerson("Мама", month = 3, day = 4),
                SharedPerson("Серёга Иванов", month = 11, day = 10, year = 1980),
                SharedPerson("Вася", month = 3, day = 5, year = 1990),
                SharedPerson("Петя", month = 2, day = 1, year = 1985),
                SharedPerson("Тётя Света", month = 7, day = 21),
                SharedPerson("Ваня", month = 5, day = 7),
            ),
            r.people,
        )
        // 31.02 — нет такой даты; «просто строка» — без даты.
        assertEquals(listOf(7, 8), r.skipped)
    }

    @Test
    fun `двузначный год`() {
        assertEquals(2005, BirthdayText.parse("Кирилл 01.01.05", 2026).people.single().year)
        assertEquals(1997, BirthdayText.parse("Аня 10.11.97", 2026).people.single().year)
    }
}
