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
    fun `важная дата и период ставятся на компьютере и доезжают через файл`() {
        val (d1, id) = Edits.saveMoment(data(), null, "Отпуск", "море", today, com.sonnik.pin.engine.Moments.endOf(today, 17), listOf("#отдых"), t0, "Отпуска")
        val e = roundTrip(d1).byId.getValue(id)
        assertEquals(com.sonnik.pin.engine.EntryType.MOMENT, e.entryType)
        assertEquals(today, e.momentDay)
        assertEquals(today + 16, e.momentEnd)
        assertEquals(listOf("отдых"), e.tagList)
        assertEquals("Отпуска", e.momentGroup)
        assertEquals(null, e.dueDay)
        // Не появляется среди дел дня.
        assertTrue(roundTrip(d1).dayItemsBetween(today, today + 30, today).none { it.entry.id == id })
        val (d2, _) = Edits.saveMoment(roundTrip(d1), id, "Отпуск", "море", today, null, emptyList(), t0 + 1000)
        assertEquals(null, roundTrip(d2).byId.getValue(id).momentEnd)
    }

    @Test
    fun `несколько напоминаний ставятся на компьютере и доезжают через файл`() {
        val (d1, id) = Edits.create(data(), Draft("Созвон", day = today, timeMinutes = 18 * 60, reminders = listOf(24 * 60, 120)), t0)
        val e = roundTrip(d1).byId.getValue(id)
        assertEquals(listOf(120, 24 * 60), e.reminderList)
        assertEquals(listOf(120, 24 * 60), Draft.of(e).reminders)
        val d2 = Edits.update(roundTrip(d1), id, Draft.of(e).copy(reminders = emptyList()), today, t0 + 1000)
        assertEquals(emptyList<Int>(), roundTrip(d2).byId.getValue(id).reminderList)
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
        assertTrue(back.journal.none { it.event == JournalEvent.DONE })
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

    @Test
    fun `человек с днём рождения и удаление вместе с ним`() {
        val (d, pid) = Edits.savePerson(data(), null, "Аня", "жена", "любит тюльпаны", Edits.Birthday(11, 29, 1975), listOf(7, 1), today, t0)
        val read = roundTrip(d)
        assertEquals("WIFE", read.people.single().relation)
        assertEquals(Edits.Birthday(11, 29, 1975), Edits.birthdayOf(read, pid))
        val bday = read.entries.single { it.entryType == EntryType.BIRTHDAY }
        assertEquals(LocalDate.of(2026, 11, 29).toEpochDay(), bday.dueDay)
        assertEquals("Аня", read.monthEvents(today, today + 30).single().name)
        val gone = roundTrip(Edits.deletePerson(read, pid, t0 + 1000))
        assertTrue(gone.people.single().deleted)
        assertTrue(gone.monthEvents(today, today + 30).isEmpty())
    }

    @Test
    fun `план становится задачей`() {
        val (d, id) = Edits.savePlan(data(), null, "Покрасить забор", "- [ ] краска", com.sonnik.pin.engine.PlanScope.Someday, emptyList(), t0)
        assertEquals(EntryType.PLAN, roundTrip(d).byId.getValue(id).entryType)
        val task = roundTrip(Edits.planToTask(d, id, today + 2, 10 * 60, t0 + 1000)).byId.getValue(id)
        assertEquals(EntryType.ONE_TIME, task.entryType)
        assertEquals(today + 2, task.dueDay)
        assertEquals(10 * 60, task.timeMinutes)
    }

    @Test
    fun `отметка привычки и снятие не возвращается из файла`() {
        val (d, id) = Edits.saveHabit(data(), null, "Зарядка", null, 8 * 60, today, t0)
        val marked = Edits.toggleHabit(d, id, today, today, t0 + 1000)
        val onDisk = roundTrip(marked)
        assertEquals(1, onDisk.journal.size)
        val unmarked = Edits.toggleHabit(onDisk, id, today, today, t0 + 2000)
        // Файл (или другое устройство) ещё со старой отметкой — после слияния её нет, остаётся «надгробие».
        val merged = Edits.merge(unmarked, onDisk)
        assertTrue(merged.journal.none { it.event == JournalEvent.DONE })
        assertEquals(listOf("-DONE"), merged.journal.map { it.event })
        val again = roundTrip(Edits.merge(onDisk, roundTrip(merged)))
        assertTrue(again.habitDays[id].orEmpty().isEmpty())
        // Будущее не отмечается.
        assertEquals(0, Edits.toggleHabit(d, id, today + 1, today, t0).journal.size)
    }

    @Test
    fun `день рождения из поля`() {
        assertEquals(Edits.Birthday(11, 29, 1975), parseBirthday("29.11.1975"))
        assertEquals(Edits.Birthday(2, 29, null), parseBirthday("29.02"))
        assertNull(parseBirthday("31.02.2001"))
    }

    @Test
    fun `чистый лист — пустой архив читается и в него пишется`() {
        val empty = PinData.read(Edits.toBytes(PinData.EMPTY, zone), zone)
        assertTrue(empty.entries.isEmpty())
        val (d, id) = Edits.create(empty, Draft("Первая задача", day = today), t0)
        assertEquals("Первая задача", roundTrip(d).byId.getValue(id).title)
    }

    @Test
    fun `на завтра, в планы и пропуск повтора`() {
        val d = data(entry("1", "Хвост", today - 3))
        val tomorrow = Edits.reschedule(d, "1", today + 1, t0 + 1000).byId.getValue("1")
        assertEquals(today + 1, tomorrow.dueDay)
        assertEquals(today + 1, tomorrow.originalDay)
        assertEquals(0, tomorrow.rollovers)
        val plan = roundTrip(Edits.moveToPlans(d, "1", t0 + 1000)).byId.getValue("1")
        assertEquals(EntryType.PLAN, plan.entryType)
        assertNull(plan.dueDay)
        val (r, id) = Edits.create(data(), Draft("Отчёт", day = today - 2, repeat = RepeatRule.parse("daily")), t0)
        val skipped = Edits.skipOccurrence(r, id, today, t0 + 1000)
        assertEquals(today + 1, skipped.byId.getValue(id).dueDay)
        assertEquals(JournalEvent.MISSED, skipped.journal.single().event)
    }

    @Test
    fun `пункт описания отмечается в строке задачи`() {
        val d = data(entry("1", "Покупки", today).copy(body = "- [ ] хлеб\n- [ ] молоко"))
        val e = d.byId.getValue("1")
        val toggled = Edits.setBody(d, "1", com.sonnik.pin.engine.Markdown.toggleTask(e.body, 1), t0 + 1000)
        assertEquals("- [ ] хлеб\n- [x] молоко", toggled.byId.getValue("1").body)
    }

    @Test
    fun `минуты фокуса пишутся в журнал и видны в итогах`() {
        val d = Edits.logFocus(data(entry("1", "Отчёт", today)), "1", 25, t0, today)
        val read = roundTrip(d)
        assertEquals(25, focusMinutes(read.statEvents(), today))
        assertEquals(0, Edits.logFocus(read, null, 0, t0, today).journal.size - read.journal.size)
    }

    @Test
    fun `матрица берёт перенос и важность`() {
        val d = data(entry("1", "Важное", today - 2).copy(priority = Priority.HIGH.level), entry("2", "Потом", today + 10))
        val board = com.sonnik.pin.engine.Matrix.build(d.matrixItems(today), today)
        assertEquals(listOf("1"), board.getValue(com.sonnik.pin.engine.Quadrant.DO).map { it.id })
        assertEquals(listOf("2"), board.getValue(com.sonnik.pin.engine.Quadrant.LATER).map { it.id })
        val important = Edits.setPriority(d, "2", Priority.HIGH, t0 + 1)
        assertEquals(Priority.HIGH.level, important.byId.getValue("2").priority)
    }

    @Test
    fun `подарки, праздники и списки`() {
        val (d, pid) = Edits.savePerson(data(), null, "Аня", "жена", "", null, emptyList(), today, t0)
        val (d2, gid) = Edits.saveGift(d, null, Edits.Gift("Шарф", pid, null, "1500", "", com.sonnik.pin.engine.GiftStatus.IDEA), t0 + 1)
        val bought = roundTrip(Edits.setGiftStatus(d2, gid, com.sonnik.pin.engine.GiftStatus.BOUGHT, t0 + 2))
        assertEquals("BOUGHT", bought.byId.getValue(gid).giftStatus)
        assertEquals("1500", bought.byId.getValue(gid).price)

        val seeded = Edits.seedHolidays(data(), today, t0)
        assertEquals(com.sonnik.pin.engine.Holidays.RU.size, seeded.entries.size)
        // Повторно не задваивает.
        assertEquals(seeded.entries.size, Edits.seedHolidays(seeded, today, t0).entries.size)
        val id = com.sonnik.pin.engine.Holidays.RU.first().entryId
        assertTrue(roundTrip(Edits.setDisabled(seeded, id, true, t0 + 1)).byId.getValue(id).disabled)

        val (l, lid) = Edits.saveList(data(entry("1", "Купить краску", today)), null, "Ремонт", com.sonnik.pin.engine.ListColor.GREEN, t0)
        val inList = Edits.update(l, "1", Draft("Купить краску", day = today, listId = lid), today, t0 + 1)
        val removed = roundTrip(Edits.deleteList(inList, lid, t0 + 2))
        assertTrue(removed.lists.single().deleted)
        assertNull(removed.byId.getValue("1").listId)
    }

    @Test
    fun `напоминания о задаче, поводы и сводка`() {
        val (d, pid) = Edits.savePerson(data(), null, "Аня", "жена", "", Edits.Birthday(11, 5, 1990), listOf(1), today, t0)
        val (d2, id) = Edits.create(d, Draft("Позвонить", day = today - 1, timeMinutes = 18 * 60), t0)
        val snaps = d2.openSnapshots(today)
        // Вчерашняя задача переехала на сегодня — и напоминание тоже на сегодня в 18:00.
        assertEquals(today, snaps.single { it.id == id }.currentDay)
        val at = com.sonnik.pin.engine.Reminders.dayAt(today, 18 * 60, zone, 240)
        assertEquals(listOf(id), com.sonnik.pin.engine.Reminders.due(snaps, at - 1000, at + 1000, zone, 240).map { it.taskId })
        val (_, notices) = d2.occasionTexts(today)
        assertTrue(notices.single().startsWith("Завтра — день рождения: Аня"))
        assertTrue(pid.isNotEmpty())
    }

    @Test
    fun `импорт людей из zip — новых добавляет, своим дописывает дату`() {
        val (d, _) = Edits.savePerson(data(), null, "Мама", "мама", "своя заметка", null, emptyList(), today, t0)
        val file = com.sonnik.pin.engine.PeopleFile.zip(
            listOf(
                com.sonnik.pin.engine.SharedPerson("Аня", "WIFE", "тюльпаны", 11, 29, 1975),
                com.sonnik.pin.engine.SharedPerson("мама", "MOTHER", "чужая заметка", 3, 4, null),
            ),
        )
        val parsed = com.sonnik.pin.engine.PeopleFile.parse(file, 2026)
        val (imported, n) = Edits.importPeople(d, parsed.people, today, t0 + 1)
        assertEquals(2, n)
        val read = roundTrip(imported)
        assertEquals(setOf("Аня", "Мама"), read.people.map { it.name }.toSet())
        val mom = read.people.single { it.name == "Мама" }
        assertEquals("своя заметка", mom.note)
        assertEquals(Edits.Birthday(3, 4, null), Edits.birthdayOf(read, mom.id))
        // Повторный импорт ничего не задваивает.
        assertEquals(0, Edits.importPeople(read, parsed.people, today, t0 + 2).second)
        // И обратно в файл.
        val shared = Edits.sharedPeople(read, read.people.map { it.id })
        assertEquals(listOf("Аня", "Мама"), com.sonnik.pin.engine.PeopleFile.parse(com.sonnik.pin.engine.PeopleFile.zip(shared), 2026).people.map { it.name })
    }

    @Test
    fun `календарь — просроченный повтор на сегодня, лимит вложений`() {
        val (d, id) = Edits.create(data(), Draft("Отчёт", day = today - 3, repeat = RepeatRule.parse("weekly mon,tue,wed,thu,fri,sat,sun")), t0)
        val items = d.dayItemsBetween(today - 7, today + 1, today)
        assertTrue(items.none { it.kind == com.sonnik.pin.engine.MarkKind.OPEN && it.day < today && it.entry.id == id })
        assertTrue(items.any { it.kind == com.sonnik.pin.engine.MarkKind.OPEN && it.day == today && it.entry.id == id })

        var big = data(entry("1", "Файлы", today))
        repeat(5) { big = Edits.addAttachment(big, "1", "f$it.bin", ByteArray(10 * 1024 * 1024), t0 + it) }
        val r = runCatching { Edits.addAttachment(big, "1", "ещё.bin", ByteArray(1024), t0 + 10) }
        assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("50 МБ"))
    }
}
