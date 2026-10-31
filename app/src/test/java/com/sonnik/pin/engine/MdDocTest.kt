package com.sonnik.pin.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MdDocTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `запись и чтение туда и обратно`() {
        val doc = MdDoc(
            linkedMapOf(
                "id" to "6f1c",
                "type" to "one_time",
                "title" to "Купить: хлеб, \"бородинский\"",
                "due" to "2026-11-05",
                "time" to "18:30",
                "tags" to listOf("дом", "магазин"),
                "rollovers" to 2,
                "flag" to "true",
            ),
            "Тело\n\n- [ ] пункт\n",
        )
        val text = doc.render()
        assertTrue(text.contains("due: 2026-11-05\n"))
        assertTrue(text.contains("time: \"18:30\"\n"))
        assertTrue(text.contains("tags: [дом, магазин]\n"))
        val back = MdDoc.parse(text)
        assertEquals("Купить: хлеб, \"бородинский\"", back.string("title"))
        assertEquals(LocalDate.of(2026, 11, 5).toEpochDay(), back.day("due"))
        assertEquals("18:30", back.string("time"))
        assertEquals(listOf("дом", "магазин"), back.list("tags"))
        assertEquals(2, back.int("rollovers"))
        assertEquals("true", back.string("flag"))
        assertEquals("Тело\n\n- [ ] пункт\n", back.body)
    }

    @Test
    fun `ручная правка, списки через дефис, комментарии и CRLF`() {
        val text = "---\r\nid: abc\r\ntitle: 'Ман''ка'\r\ntags:\r\n  - дом\r\n  - \"сад\"\r\nempty:\r\npriority: high # важно\r\n---\r\n# Заголовок\r\n"
        val doc = MdDoc.parse(text)
        assertEquals("abc", doc.string("id"))
        assertEquals("Ман'ка", doc.string("title"))
        assertEquals(listOf("дом", "сад"), doc.list("tags"))
        assertNull(doc.string("empty"))
        assertEquals("high", doc.string("priority"))
        assertEquals("# Заголовок\n", doc.body)
    }

    @Test
    fun `файл без шапки и даты`() {
        assertEquals("просто текст", MdDoc.parse("просто текст").body)
        assertTrue(MdDoc.parse("просто текст").fields.isEmpty())
        val at = LocalDateTime.of(2026, 11, 5, 8, 12, 44).atZone(zone).toInstant().toEpochMilli()
        assertEquals("2026-11-05T08:12:44+03:00", MdDoc.formatInstant(at, zone))
        assertEquals(at, MdDoc.parseInstant("2026-11-05T08:12:44+03:00", zone))
        assertEquals(at, MdDoc.parseInstant("2026-11-05T08:12:44", zone))
        assertEquals("Отчёт за 05 11", MdDoc.fileName("Отчёт: за 05/11"))
        assertEquals("без названия", MdDoc.fileName("  ...  "))
    }

    @Test
    fun `журнал месяца`() {
        val day = LocalDate.of(2026, 11, 3).toEpochDay()
        val rows = listOf(
            JournalTable.Row(day, "ROLLED", "id-1", 1_000_000L, "ONE_TIME", 1, 1, false, day),
            JournalTable.Row(day + 2, "DONE", "id-1", 2_000_000L, "RECURRING", 2, 2, true, day),
        )
        val text = JournalTable.render("2026-11", rows, zone)
        assertTrue(text.contains("| 2026-11-05 | DONE | id-1 |"))
        assertEquals(rows, JournalTable.parse(text, zone))
        // Столбцы в другом порядке и мусорные строки.
        val manual = "| id | event | day |\n|---|---|---|\n| x | done | 2026-11-01 |\n| | | |\n"
        val parsed = JournalTable.parse(manual, zone).single()
        assertEquals("DONE", parsed.event)
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), parsed.day)
    }
}
