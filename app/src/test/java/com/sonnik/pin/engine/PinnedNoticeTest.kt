package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedNoticeTest {
    private val today = 20_400L

    private fun task(id: String, title: String, day: Long = today, time: Int? = null, priority: Priority = Priority.NORMAL) =
        TaskSnapshot(id, EntryType.ONE_TIME, TaskStatus.OPEN, day, day, time, priority, title = title)

    private fun board(vararg t: TaskSnapshot) = TodayBoard.build(t.toList(), today, doneDayOf = { null })

    @Test
    fun `коротко — сколько и первые три`() {
        val n = PinnedNotice.of(
            board(
                task("1", "Хлеб"),
                task("2", "Созвон", time = 18 * 60),
                task("3", "Отчёт", priority = Priority.HIGH),
                task("4", "Позвонить маме"),
                task("5", "Старое", day = today - 2),
            ),
        )
        assertEquals("5 на сегодня · 1 перенесена", n.title)
        // Важное, потом со временем; перенесённое без времени — раньше остальных.
        assertEquals("Отчёт · 18:00 Созвон · Старое +2", n.text)
        // При раскрытии — все, каждая отдельной строкой, полные названия.
        assertEquals(listOf("❗ Отчёт", "18:00 Созвон", "↻ Старое", "Хлеб", "Позвонить маме"), n.lines)
    }

    @Test
    fun `длинное название обрезается, пустой день`() {
        val n = PinnedNotice.of(board(task("1", "Очень длинное название задачи, которое не влезет")))
        assertEquals("1 на сегодня", n.title)
        assertEquals("Очень длинное название зада…", n.text)
        assertEquals(listOf("Очень длинное название задачи, которое не влезет"), n.lines)
        val many = PinnedNotice.of(board(*Array(15) { task("$it", "Дело $it") }))
        assertEquals(13, many.lines.size)
        assertEquals("…и ещё 3", many.lines.last())
        assertEquals("На сегодня задач нет", PinnedNotice.of(board()).title)
        assertEquals(PinnedNotice("✓ Всё на сегодня сделано", "Выполнено: 2"), PinnedNotice.of(board(), doneToday = 2))
        // Будущие задачи — не на сегодня.
        assertEquals("На сегодня задач нет", PinnedNotice.of(board(task("1", "Завтра", day = today + 1))).title)
    }
}
