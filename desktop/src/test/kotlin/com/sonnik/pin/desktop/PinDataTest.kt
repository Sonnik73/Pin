package com.sonnik.pin.desktop

import com.sonnik.pin.data.Archive
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.data.MdFiles
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.engine.Attachments
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.MarkKind
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.TaskStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinDataTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 4).toEpochDay()
    private val t0 = 1_780_000_000_000L

    private fun entry(id: String, title: String, day: Long?, type: EntryType = EntryType.ONE_TIME, updated: Long = t0) = EntryEntity(
        id = id, type = type.name, title = title, dueDay = day, originalDay = day, createdAt = t0, updatedAt = updated,
    )

    private fun zip(
        entries: List<EntryEntity>,
        people: List<PersonEntity> = emptyList(),
        journal: List<JournalEntity> = emptyList(),
        lists: List<ListEntity> = emptyList(),
        blobs: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray = Archive.toBytes(MdFiles.export(entries, people, journal, zone, includeDeleted = true, lists = lists), blobs)

    @Test
    fun `читает Pin_zip телефона и раскладывает «Сегодня»`() {
        val home = ListEntity("lh", "Дом", "GREEN", createdAt = t0, updatedAt = t0)
        val bytes = zip(
            listOf(
                entry("1", "Купить хлеб", today).copy(priority = Priority.HIGH.level, listId = "lh"),
                entry("2", "Старая задача", today - 2),
                entry("3", "Завтрашняя", today + 1),
                entry("4", "Готово", today).copy(status = TaskStatus.DONE.name, completedDay = today, completedAt = t0),
                entry("5", "Удалённая", today).copy(deleted = true),
            ),
            lists = listOf(home),
        )
        val data = PinData.read(bytes, zone)
        val board = data.board(today)
        assertEquals(listOf("2"), board.rolledOver.map { it.id })
        // Перенос досчитан для показа: две ночи.
        assertEquals(2, board.rolledOver.single().rollovers)
        assertEquals(listOf("1"), board.today.map { it.id })
        assertEquals(listOf("4"), board.done.map { it.id })
        assertEquals(listOf("3"), board.upcoming.map { it.id })
        assertEquals("Дом", data.listsById.getValue("lh").name)
    }

    @Test
    fun `вложения читаются из архива`() {
        val e = entry("1", "Счёт", today).copy(attachments = "a1b2c3d4/Счёт.pdf")
        val data = PinData.read(zip(listOf(e), blobs = listOf("attachments/a1b2c3d4/Счёт.pdf" to byteArrayOf(7, 8))), zone)
        val a = Attachments.parse(data.byId.getValue("1").attachments).single()
        assertTrue(data.blob(a)!!.contentEquals(byteArrayOf(7, 8)))
    }

    @Test
    fun `календарь: повторы, выполненное и дни рождения`() {
        val anna = PersonEntity("p1", "Аня", "FRIEND", createdAt = t0, updatedAt = t0)
        val birthday = entry("b", "Поздравить: Аня", today + 3, EntryType.BIRTHDAY).copy(
            personId = "p1", birthYear = 1990, repeatRule = LocalDate.ofEpochDay(today + 3).let { "yearly %02d-%02d".format(it.monthValue, it.dayOfMonth) },
            repeatAnchor = today + 3,
        )
        val daily = entry("r", "Зарядка", today, EntryType.RECURRING).copy(repeatRule = "daily", repeatAnchor = today)
        val doneEvent = JournalEntity(entryId = "1", event = JournalEvent.DONE, day = today - 1, at = t0, kind = "ONE_TIME", priority = 1, rollovers = 0)
        val data = PinData.read(
            zip(listOf(birthday, daily, entry("1", "Вчерашняя", today - 1).copy(status = "DONE", completedDay = today - 1)), listOf(anna), listOf(doneEvent)),
            zone,
        )
        val items = data.dayItemsBetween(today - 1, today + 3, today)
        assertTrue(items.any { it.day == today + 2 && it.kind == MarkKind.PROJECTED && it.entry.id == "r" })
        assertTrue(items.any { it.day == today - 1 && it.kind == MarkKind.DONE && it.entry.id == "1" })
        val marks = data.monthMarks(today - 1, today + 3, today)
        assertEquals(true, marks[today + 3]?.birthday)
        val ev = data.monthEvents(today, today + 7).single { it.birthday }
        assertEquals("Аня", ev.name)
        assertNotNull(ev.age)
    }

    @Test
    fun `из двух копий записи берётся более новая`() {
        val files = MdFiles.export(listOf(entry("1", "Старое имя", today)), emptyList(), emptyList(), zone, true) +
            MdFiles.export(listOf(entry("1", "Новое имя", today, updated = t0 + 60_000)), emptyList(), emptyList(), zone, true)
                .map { it.copy(path = "tasks/копия.md") }
        val data = PinData.fromFiles(files.map { it.path to it.text }, emptyList(), zone, t0)
        assertEquals("Новое имя", data.byId.getValue("1").title)
    }
}
