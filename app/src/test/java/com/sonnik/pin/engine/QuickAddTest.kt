package com.sonnik.pin.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAddTest {
    // Воскресенье, 1 ноября 2026.
    private val today = LocalDate.of(2026, 11, 1).toEpochDay()
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day).toEpochDay()

    private fun parse(s: String) = QuickAdd.parse(s, today)

    @Test
    fun `относительные дни`() {
        assertEquals(QuickDraft("Купить хлеб", today + 1, recognized = listOf("завтра")), parse("завтра купить хлеб"))
        assertEquals(QuickDraft("Купить хлеб", today + 1, list = "дом", recognized = listOf("завтра")), parse("завтра купить хлеб @дом"))
        assertEquals(today + 2, parse("послезавтра отчёт").day)
        assertEquals(today, parse("Сегодня зарядка").day)
        assertEquals("Зарядка", parse("Сегодня зарядка").title)
        assertEquals(today, parse("просто задача").day)
        assertEquals(today + 3, parse("через 3 дня полить цветы").day)
        assertEquals(today + 14, parse("через 2 недели к врачу").day)
        assertEquals(today + 7, parse("через неделю созвон").day)
        assertEquals(d(12, 1), parse("через месяц продлить").day)
    }

    @Test
    fun `дни недели`() {
        assertEquals(d(11, 6), parse("в пятницу позвонить маме").day)
        assertEquals("Позвонить маме", parse("в пятницу позвонить маме").title)
        assertEquals(d(11, 2), parse("во вторник что-то").day - 1)
        assertEquals(today, parse("в воскресенье отдых").day)
        assertEquals(d(11, 4), parse("отчёт ср").day)
    }

    @Test
    fun `даты`() {
        assertEquals(d(11, 12), parse("12 ноября отчёт").day)
        assertEquals("Отчёт", parse("12 ноября отчёт").title)
        assertEquals(d(1, 1, 2027), parse("1 янв салют").day)
        assertEquals(d(10, 5, 2027), parse("5 октября техосмотр").day) // уже прошло — в следующем году
        assertEquals(d(3, 8, 2028), parse("8 марта 2028 цветы").day)
        assertEquals(d(11, 12), parse("12.11 сдать").day)
        assertEquals(d(2, 1, 2027), parse("сдать 01.02.2027").day)
    }

    @Test
    fun `время, важность и теги`() {
        val q = parse("завтра в 18:30 позвонить маме ! #семья #звонки")
        assertEquals(today + 1, q.day)
        assertEquals(18 * 60 + 30, q.timeMinutes)
        assertEquals(Priority.HIGH, q.priority)
        assertEquals(listOf("семья", "звонки"), q.tags)
        assertEquals("Позвонить маме", q.title)
        assertEquals(9 * 60, parse("в пятницу в 9 планёрка").timeMinutes)
        assertEquals(7 * 60 + 5, parse("зарядка 7:05").timeMinutes)
        assertNull(parse("купить 3 банана").timeMinutes)
        assertEquals("завтра, 18:30, важная, #семья, #звонки", QuickAdd.describe(q, today))
    }

    @Test
    fun `числа в названии не путаются с датой`() {
        val q = parse("в 12 ноября встреча")
        assertEquals(d(11, 12), q.day)
        assertNull(q.timeMinutes)
        assertEquals("Встреча", q.title)
        assertEquals("Хлеб", parse("на завтра хлеб").title)
        assertEquals("Купить 2 кг яблок", parse("купить 2 кг яблок").title)
    }

    @Test
    fun `подсветка — позиции распознанного в исходной строке`() {
        val input = "завтра в 18:30 позвонить маме ! #семья"
        val q = parse(input)
        val marks = QuickAdd.marks(input, q)
        // «завтра» — дата, «в 18:30» — время, «!» — важность, «#семья» — тег; всё в порядке следования.
        assertEquals(
            listOf(
                QuickAdd.QuickMark(0, 6, QuickAdd.QuickMarkKind.DATE),
                QuickAdd.QuickMark(7, 14, QuickAdd.QuickMarkKind.TIME),
                QuickAdd.QuickMark(30, 31, QuickAdd.QuickMarkKind.PRIORITY),
                QuickAdd.QuickMark(32, 38, QuickAdd.QuickMarkKind.TAG),
            ),
            marks,
        )
    }

    @Test
    fun `подсветка — предлог уходит в дату, повтор фразы не путается`() {
        val input = "на завтра завтра в 9 зарядка"
        val marks = QuickAdd.marks(input, parse(input))
        // Дата — «на завтра» (с предлогом) на первом вхождении; «завтра» в названии не трогаем.
        assertEquals(
            listOf(QuickAdd.QuickMark(0, 9, QuickAdd.QuickMarkKind.DATE), QuickAdd.QuickMark(17, 20, QuickAdd.QuickMarkKind.TIME)),
            marks,
        )
    }

    @Test
    fun `подсветка — без распознанного пусто, тег с заглавной находится`() {
        assertEquals(emptyList<QuickAdd.QuickMark>(), QuickAdd.marks("купить 3 банана", parse("купить 3 банана")))
        val input = "#Работа отчёт в пятницу"
        val marks = QuickAdd.marks(input, parse(input))
        assertEquals(QuickAdd.QuickMarkKind.TAG, marks.first { it.kind == QuickAdd.QuickMarkKind.TAG }.kind)
        assertEquals(0, marks.first { it.kind == QuickAdd.QuickMarkKind.TAG }.start)
    }
}
