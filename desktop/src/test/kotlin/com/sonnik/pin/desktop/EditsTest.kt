package com.sonnik.pin.desktop

import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.engine.Attachments
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.QuickAdd
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.TaskStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 4).toEpochDay()
    private val t0 = 1_780_000_000_000L

    private fun entry(id: String, title: String, day: Long, updated: Long = t0) = EntryEntity(
        id = id, type = EntryType.ONE_TIME.name, title = title, dueDay = day, originalDay = day, createdAt = t0, updatedAt = updated,
    )

    private fun data(vararg e: EntryEntity) = PinData(e.toList(), emptyList(), emptyList(), emptyList(), emptyMap())

    /** Записать и прочитать заново — как увидит телефон. */
    private fun roundTrip(d: PinData) = PinData.read(Edits.toBytes(d, zone), zone)

    @Test
    fun `новая задача и правка доезжают через файл`() {
        val (d1, id) = Edits.create(data(), Draft("Позвонить маме", day = today, timeMinutes = 18 * 60, priority = Priority.HIGH, tags = listOf("#Дом")), t0)
        val d2 = Edits.update(roundTrip(d1), id, Draft("Позвонить маме и папе", day = today + 1, timeMinutes = 19 * 60), today, t0 + 1000)
        val e = roundTrip(d2).byId.getValue(id)
        assertEquals("Позвонить маме и папе", e.title)
        assertEquals(today + 1, e.dueDay)
        assertEquals(19 * 60, e.timeMinutes)
        assertEquals("", e.tags)
        assertEquals(Priority.NORMAL.level, e.priority)
        assertEquals(EntryType.ONE_TIME, e.entryType)
    }

    @Test
    fun `отметка выполнения пишет журнал, снятие — убирает`() {
        val d = data(entry("1", "Задача", today - 2))
        val done = Edits.setDone(d, "1", true, today, t0 + 5000)
        val e = done.byId.getValue("1")
        assertEquals(TaskStatus.DONE, e.entryStatus)
        assertEquals(today, e.completedDay)
        // Перенос досчитан: две ночи.
        assertEquals(2, e.rollovers)
        val event = done.journal.single()
        assertEquals(JournalEvent.DONE, event.event)
        assertFalse(event.onTime)
        val back = Edits.setDone(roundTrip(done), "1", false, today, t0 + 9000)
        assertEquals(TaskStatus.OPEN, back.byId.getValue("1").entryStatus)
        assertTrue(back.journal.isEmpty())
    }

    @Test
    fun `периодическая встаёт на следующий повтор`() {
        val (d, id) = Edits.create(data(), Draft("Зарядка", day = today, repeat = RepeatRule.parse("daily")), t0)
        val done = Edits.setDone(d, id, true, today, t0 + 1000)
        assertEquals(today + 1, done.byId.getValue(id).dueDay)
        assertEquals(today, done.byId.getValue(id).completedDay)
        val back = Edits.setDone(done, id, false, today, t0 + 2000)
        assertEquals(today, back.byId.getValue(id).dueDay)
        assertNull(back.byId.getValue(id).completedDay)
    }

    @Test
    fun `быстрое добавление создаёт список`() {
        val (d, id) = Edits.quickAdd(data(), QuickAdd.parse("завтра в 18 купить краску @ремонт !", today), t0)
        val e = d.byId.getValue(id)
        assertEquals(today + 1, e.dueDay)
        assertEquals(Priority.HIGH.level, e.priority)
        assertEquals("Ремонт", d.listsById.getValue(e.listId!!).name)
        // Второй раз тот же список не создаётся.
        val (d2, id2) = Edits.quickAdd(d, QuickAdd.parse("кисти @Ремонт", today), t0)
        assertEquals(1, d2.lists.size)
        assertEquals(e.listId, d2.byId.getValue(id2).listId)
    }

    @Test
    fun `слияние с файлом — новее побеждает, журнал без дублей`() {
        val local = Edits.setDone(data(entry("1", "Моя правка", today, updated = t0 + 10_000), entry("2", "Старое", today)), "1", true, today, t0 + 20_000)
        val phone = data(entry("1", "Старое имя", today, updated = t0 + 5_000), entry("2", "Правка с телефона", today, updated = t0 + 30_000))
            .let { PinData(it.entries + entry("3", "Новая с телефона", today), it.people, it.lists, local.journal, it.blobs) }
        val merged = Edits.merge(local, roundTrip(phone))
        assertEquals("Моя правка", merged.byId.getValue("1").title)
        assertEquals("Правка с телефона", merged.byId.getValue("2").title)
        assertEquals("Новая с телефона", merged.byId.getValue("3").title)
        assertEquals(1, merged.journal.size)
    }

    @Test
    fun `вложения добавляются и убираются вместе с файлом`() {
        val d = Edits.addAttachment(data(entry("1", "Счёт", today)), "1", "Счёт за май.pdf", byteArrayOf(1, 2, 3), t0 + 1000)
        val a = Attachments.parse(d.byId.getValue("1").attachments).single()
        val read = roundTrip(d)
        assertTrue(read.blob(a)!!.contentEquals(byteArrayOf(1, 2, 3)))
        val removed = Edits.removeAttachment(read, "1", a.id, t0 + 2000)
        assertNull(removed.byId.getValue("1").attachments)
        assertNull(roundTrip(removed).blob(a))
    }

    @Test
    fun `удаление — пометкой`() {
        val d = Edits.delete(data(entry("1", "Лишнее", today)), "1", t0 + 1000)
        val read = roundTrip(d)
        assertTrue(read.byId.getValue("1").deleted)
        assertTrue(read.board(today).today.isEmpty())
    }

    @Test
    fun `дата и время из полей окна`() {
        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), DateText.parse("31.12.2026", today))
        assertEquals(LocalDate.of(2026, 3, 8).toEpochDay(), DateText.parse("8.3", today))
        assertNull(DateText.parse("32.1.2026", today))
        assertEquals(18 * 60 + 30, DateText.parseTime("18:30"))
        assertEquals(9 * 60, DateText.parseTime("9"))
        assertNull(DateText.parseTime("25:00"))
        assertEquals("4.11.2026", DateText.format(today))
    }
}
