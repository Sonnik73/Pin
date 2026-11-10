package com.sonnik.pin.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MomentsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    @Test
    fun `сколько прошло — годы, месяцы, дни`() {
        assertEquals("0 дней", Moments.span(d(2026, 11, 10), d(2026, 11, 10)))
        assertEquals("1 день", Moments.span(d(2026, 11, 10), d(2026, 11, 11)))
        assertEquals("1 месяц", Moments.span(d(2026, 11, 26), d(2026, 12, 26)))
        assertEquals("1 год 1 месяц 3 дня", Moments.span(d(2026, 11, 10), d(2027, 12, 13)))
        assertEquals("5 лет 2 месяца", Moments.span(d(2027, 1, 10), d(2021, 11, 10)))
        assertEquals("21 день", Moments.days(21))
    }

    @Test
    fun `счётчик одной даты`() {
        val award = d(2026, 11, 10)
        assertEquals(Moments.Counter("Сегодня", null), Moments.counter(award, null, award))
        assertEquals("Прошло 2 дня", Moments.counter(award, null, award + 2).main)
        assertEquals("Прошло 1 год 1 месяц 3 дня (398 дней)", Moments.counter(award, null, d(2027, 12, 13)).main)
        assertEquals("Через 5 дней", Moments.counter(award, null, award - 5).main)
        assertNull(Moments.counter(award, null, award + 2).detail)
    }

    @Test
    fun `отпуск с 10 ноября на 17 дней`() {
        val start = d(2026, 11, 10)
        val end = Moments.endOf(start, 17)
        assertEquals(d(2026, 11, 26), end)
        assertEquals(17L, Moments.length(start, end))
        assertEquals("10–26 ноября 2026 · 17 дней", Moments.describe(start, end))
        assertEquals("продлится 17 дней", Moments.counter(start, end, start - 5).detail)
        assertEquals("идёт: день 1 из 17 · осталось 16 дней", Moments.counter(start, end, start).detail)
        assertEquals(Moments.Counter("Прошло 2 дня", "идёт: день 3 из 17 · осталось 14 дней"), Moments.counter(start, end, d(2026, 11, 12)))
        assertEquals("идёт: день 17 из 17 · последний день", Moments.counter(start, end, end).detail)
        assertEquals("закончился вчера", Moments.counter(start, end, end + 1).detail)
        assertEquals("закончился 1 месяц назад", Moments.counter(start, end, d(2026, 12, 26)).detail)
    }

    @Test
    fun `подпись даты и периода через год`() {
        assertEquals("10 ноября 2026", Moments.describe(d(2026, 11, 10), null))
        assertEquals("10 ноября 2026", Moments.describe(d(2026, 11, 10), d(2026, 11, 10)))
        assertEquals("28 декабря 2026 – 8 января 2027 · 12 дней", Moments.describe(d(2026, 12, 28), Moments.endOf(d(2026, 12, 28), 12)))
        assertEquals("1 марта – 2 апреля 2026 · 33 дня", Moments.describe(d(2026, 3, 1), d(2026, 4, 2)))
        assertEquals(d(2026, 1, 1), Moments.endOf(d(2026, 1, 1), 0))
    }

    @Test
    fun `период с даты по дату`() {
        val (s, e) = Moments.range(d(2026, 11, 10), d(2026, 11, 26))
        assertEquals(d(2026, 11, 10), s)
        assertEquals(17L, Moments.length(s, e))
        // Конец раньше начала — меняются местами.
        assertEquals(d(2026, 11, 1) to d(2026, 11, 10), Moments.range(d(2026, 11, 10), d(2026, 11, 1)))
        assertEquals(Moments.MAX_PERIOD_DAYS.toLong(), Moments.range(d(2000, 1, 1), d(2030, 1, 1)).let { Moments.length(it.first, it.second) })
    }

    @Test
    fun `группы и итог по группе`() {
        assertEquals("Отпуска", Moments.groupName("  отпуска  "))
        assertNull(Moments.groupName("   "))
        assertEquals(listOf("Отпуска", "Награды"), Moments.groups(listOf("Награды", "отпуска", "Отпуска", null, " ")))
        val today = d(2026, 11, 12)
        val s = Moments.summary(
            listOf(
                d(2026, 11, 10) to Moments.endOf(d(2026, 11, 10), 17), // 17 дней, все в 2026
                d(2025, 12, 25) to d(2026, 1, 3), // 10 дней, из них 3 — в 2026
                d(2026, 5, 1) to null, // просто дата
            ),
            today,
        )
        assertEquals(Moments.Summary(count = 3, dates = 1, periods = 2, periodDays = 27, daysThisYear = 20, year = 2026), s)
        assertEquals("3 записи · 2 периода — всего 27 дней · 1 событие · в 2026 году — 20 дней", s.text)
        assertEquals("4 записи", Moments.summary(List(4) { d(2026, 1, it + 1) to null }, today).text)
        assertEquals("1 запись · 1 период — всего 5 дней", Moments.summary(listOf(d(2026, 2, 1) to d(2026, 2, 5)), today).text)
    }
}
