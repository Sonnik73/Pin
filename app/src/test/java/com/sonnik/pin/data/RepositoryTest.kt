package com.sonnik.pin.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.TaskStatus
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private var now = LocalDateTime.of(2026, 11, 2, 12, 0)
    private lateinit var db: PinDatabase
    private lateinit var repo: PinRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PinRepository(db) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun today() = repo.logicalDay().today()

    @Test
    fun `перенос досчитывается и пишется в журнал по ночам`() = runBlocking {
        val day0 = today()
        val id = repo.create(TaskDraft("Купить хлеб", day = day0, priority = Priority.HIGH))
        now = now.plusDays(3)
        assertEquals(1, repo.rollover())
        assertEquals(0, repo.rollover())

        val entry = repo.get(id)!!
        assertEquals(day0 + 3, entry.dueDay)
        assertEquals(day0, entry.originalDay)
        assertEquals(3, entry.rollovers)
        val events = db.journal().between(day0, day0 + 3)
        assertEquals(listOf(day0, day0 + 1, day0 + 2), events.map { it.day })
        assertTrue(events.all { it.event == JournalEvent.ROLLED })
        assertEquals(listOf(1, 2, 3), events.map { it.rollovers })
        assertEquals(day0 + 3, repo.settings().lastRolloverDay)
    }

    @Test
    fun `выполнение после переноса — не вовремя, отмена убирает запись журнала`() = runBlocking {
        val day0 = today()
        val id = repo.create(TaskDraft("Позвонить", day = day0))
        now = now.plusDays(1)
        repo.rollover()
        repo.setDone(id, true)

        val done = repo.get(id)!!
        assertEquals(TaskStatus.DONE, done.entryStatus)
        assertEquals(day0 + 1, done.completedDay)
        val completion = db.journal().between(day0 + 1, day0 + 1).single { it.event == JournalEvent.DONE }
        assertFalse(completion.onTime)
        assertEquals(1, completion.rollovers)

        repo.setDone(id, false)
        assertEquals(TaskStatus.OPEN, repo.get(id)!!.entryStatus)
        assertTrue(db.journal().between(day0 + 1, day0 + 1).none { it.event == JournalEvent.DONE })
    }

    @Test
    fun `выполнение в ночь до границы засчитывается вчерашнему дню`() = runBlocking {
        val day0 = today()
        val id = repo.create(TaskDraft("Ночная задача", day = day0))
        now = LocalDateTime.of(2026, 11, 3, 1, 30)
        assertEquals(day0, today())
        repo.setDone(id, true)
        val entry = repo.get(id)!!
        assertEquals(day0, entry.completedDay)
        assertTrue(db.journal().between(day0, day0).single().onTime)
    }

    @Test
    fun `смена даты вручную сбрасывает исходный день и переносы`() = runBlocking {
        val day0 = today()
        val id = repo.create(TaskDraft("Отчёт", day = day0))
        now = now.plusDays(2)
        repo.rollover()
        repo.update(id, TaskDraft("Отчёт", day = day0 + 5, tags = listOf("#Работа", "работа", " дом ")))
        val entry = repo.get(id)!!
        assertEquals(day0 + 5, entry.originalDay)
        assertEquals(0, entry.rollovers)
        assertEquals(listOf("работа", "дом"), entry.tagList)
    }

    @Test
    fun `удаление — пометкой`() = runBlocking {
        val id = repo.create(TaskDraft("Лишнее", day = today()))
        repo.delete(id)
        assertTrue(repo.get(id)!!.deleted)
        assertTrue(repo.datedEntries.first().none { it.id == id })
        assertNull(repo.datedEntries.first().firstOrNull())
    }

    @Test
    fun `периодическая — выполнение ставит следующий повтор, отмена возвращает`() = runBlocking {
        val day0 = today()
        val id = repo.create(TaskDraft("Зарядка", day = day0, repeat = RepeatRule.Daily))
        repo.setDone(id, true)
        var entry = repo.get(id)!!
        assertEquals(TaskStatus.OPEN, entry.entryStatus)
        assertEquals(day0 + 1, entry.dueDay)
        assertEquals(day0, entry.completedDay)
        val done = db.journal().between(day0, day0).single()
        assertEquals(day0, done.occurrenceDay)
        assertTrue(done.onTime)

        repo.setDone(id, false)
        entry = repo.get(id)!!
        assertEquals(day0, entry.dueDay)
        assertNull(entry.completedDay)
        assertTrue(db.journal().between(day0, day0).none { it.event == JournalEvent.DONE })
    }

    @Test
    fun `периодическая — пропущенные повторы закрываются без дублей`() = runBlocking {
        val day0 = today()
        val id = repo.create(TaskDraft("Полив", day = day0, repeat = RepeatRule.EveryNDays(2)))
        now = now.plusDays(5)
        repo.rollover()
        val entry = repo.get(id)!!
        // Повторы day0 и +2 пропущены, повтор +4 висит до +6 и перенесён на +5.
        assertEquals(day0 + 4, entry.originalDay)
        assertEquals(day0 + 5, entry.dueDay)
        assertEquals(1, entry.rollovers)
        val events = db.journal().between(day0, day0 + 5)
        assertEquals(listOf(day0, day0 + 2), events.filter { it.event == JournalEvent.MISSED }.map { it.day })
        assertEquals(listOf(day0, day0 + 2, day0 + 4), events.filter { it.event == JournalEvent.ROLLED }.map { it.day })
        assertEquals(listOf(1, 1, 1), events.filter { it.event == JournalEvent.ROLLED }.map { it.rollovers })
    }

    @Test
    fun `периодическая по дням недели начинается с ближайшего подходящего дня`() = runBlocking {
        // 2 ноября 2026 — понедельник; повтор по пятницам.
        val day0 = today()
        val id = repo.create(TaskDraft("Отчёт", day = day0, repeat = RepeatRule.Weekly(setOf(java.time.DayOfWeek.FRIDAY))))
        val entry = repo.get(id)!!
        assertEquals(day0 + 4, entry.dueDay)
        assertEquals(day0, entry.repeatAnchor)
        // Правка без смены правила и даты не сбрасывает повтор.
        repo.update(id, TaskDraft("Отчёт за неделю", day = day0, repeat = RepeatRule.Weekly(setOf(java.time.DayOfWeek.FRIDAY))))
        assertEquals(day0 + 4, repo.get(id)!!.dueDay)
        assertEquals("Отчёт за неделю", repo.get(id)!!.title)
    }

    @Test
    fun `человек с днём рождения — задача «Поздравить» и напоминание за неделю`() = runBlocking {
        val day0 = today() // 2 ноября 2026
        val id = repo.savePerson(null, "Аня", Relation.WIFE.name, "", PinRepository.Birthday(11, 9, 1997), listOf(7, 1))
        val birthday = db.entries().all().single { it.entryType == EntryType.BIRTHDAY }
        assertEquals(id, birthday.personId)
        assertEquals(day0 + 7, birthday.dueDay)
        assertEquals("Поздравить: Аня", birthday.title)

        val (_, notices) = repo.occasionNotices(day0)
        assertEquals("Через 7 дней — день рождения: Аня, 29 лет — купить подарок", Occasions.describe(notices.single()))

        // Купленный подарок снимает «купить подарок».
        val gift = repo.saveGift(null, PinRepository.GiftDraft("Наушники", id, birthday.id, "", "", GiftStatus.IDEA))
        repo.setGiftStatus(gift, GiftStatus.BOUGHT)
        assertFalse(repo.occasionNotices(day0).second.single().needGift)

        // День рождения прошёл без поздравления — пропуск и следующий год, а не перенос.
        now = now.plusDays(8)
        repo.rollover()
        val next = repo.get(birthday.id)!!
        assertEquals(java.time.LocalDate.of(2027, 11, 9).toEpochDay(), next.dueDay)
        assertEquals(0, next.rollovers)
        assertEquals(JournalEvent.MISSED, db.journal().all().single().event)

        repo.deletePerson(id)
        assertTrue(repo.get(birthday.id)!!.deleted)
        assertTrue(repo.get(gift)!!.deleted)
    }

    @Test
    fun `праздники засеваются один раз и не попадают в журнал`() = runBlocking {
        repo.seedHolidays()
        repo.seedHolidays()
        val holidays = db.entries().all().filter { it.entryType == EntryType.HOLIDAY }
        assertEquals(com.sonnik.pin.engine.Holidays.RU.size, holidays.size)
        // 2 ноября: ближайший — 4 ноября.
        assertEquals(today() + 2, repo.get("holiday-unity")!!.dueDay)
        now = now.plusDays(5)
        repo.rollover()
        assertEquals(java.time.LocalDate.of(2027, 11, 4).toEpochDay(), repo.get("holiday-unity")!!.dueDay)
        assertTrue(db.journal().all().isEmpty())
        repo.setDisabled("holiday-unity", true)
        assertTrue(repo.get("holiday-unity")!!.disabled)
    }

    @Test
    fun `план превращается в задачу`() = runBlocking {
        val id = repo.savePlan(null, "Съездить в Казань", "", PlanScope.Year(2027), listOf("путешествия"))
        assertEquals("2027", repo.get(id)!!.planScope)
        assertNull(repo.get(id)!!.dueDay)
        repo.planToTask(id, today() + 3)
        val task = repo.get(id)!!
        assertEquals(EntryType.ONE_TIME, task.entryType)
        assertEquals(today() + 3, task.dueDay)
        assertNull(task.planScope)
    }

    @Test
    fun `экспорт и импорт в md без потерь`() = runBlocking {
        val day0 = today()
        val pid = repo.savePerson(null, "Аня", Relation.WIFE.name, "любит кофе", PinRepository.Birthday(11, 9, 1997), listOf(7, 1))
        val task = repo.create(TaskDraft("Купить: хлеб", "- [ ] бородинский", day0, 18 * 60, Priority.HIGH, listOf("дом"), null))
        repo.create(TaskDraft("Зарядка", day = day0, repeat = RepeatRule.Weekly(setOf(java.time.DayOfWeek.MONDAY))))
        repo.saveGift(null, PinRepository.GiftDraft("Наушники", pid, null, "12 000 ₽", "https://example.com", GiftStatus.IDEA))
        repo.savePlan(null, "Казань", "", PlanScope.Year(2027), emptyList())
        repo.setDone(task, true)
        val files = repo.exportFiles()
        assertTrue(files.any { it.path == "people/Аня.md" })
        assertTrue(files.any { it.path.startsWith("tasks/") && it.text.contains("title: \"Купить: хлеб\"") })
        assertTrue(files.any { it.path.startsWith("journal/") })

        // Импорт в пустую базу даёт те же записи.
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo2 = PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            val report = repo2.importFiles(files.map { it.path to it.text })
            assertEquals(6, report.added)
            assertTrue(report.skipped.isEmpty())
            val a = db.entries().all().associateBy { it.id }
            val b = other.entries().all().associateBy { it.id }
            assertEquals(a.keys, b.keys)
            for ((id, e) in a) assertEquals(e.copy(updatedAt = 0, createdAt = 0), b.getValue(id).copy(updatedAt = 0, createdAt = 0))
            assertEquals(db.journal().all().map { it.copy(id = 0) }, other.journal().all().map { it.copy(id = 0) })
            assertEquals("любит кофе", other.people().get(pid)!!.note)

            // Повторный импорт ничего не дублирует.
            val again = repo2.importFiles(files.map { it.path to it.text })
            assertEquals(0, again.added)
            assertEquals(0, again.journal)
        } finally {
            other.close()
        }
    }

    @Test
    fun `импорт файла, правленного руками`() = runBlocking {
        val id = repo.create(TaskDraft("Отчёт", day = today(), tags = listOf("работа")))
        val text = "---\nid: $id\ntitle: Отчёт за квартал\nunknown_field: 42\nupdated: 2030-01-01T00:00:00+03:00\n---\n- [x] готово\n"
        val report = repo.importFiles(listOf("tasks/x.md" to text, "tasks/new.md" to "# Новая задача\nбез шапки", "bad.md" to "---\n---\n"))
        assertEquals(1, report.updated)
        assertEquals(1, report.added)
        assertEquals(listOf("bad.md"), report.skipped)
        val e = repo.get(id)!!
        assertEquals("Отчёт за квартал", e.title)
        assertEquals(listOf("работа"), e.tagList) // поля, которых нет в файле, не сбрасываются
        assertEquals("- [x] готово", e.body)
        assertTrue(repo.exportFiles().first { it.text.contains(id) }.text.contains("unknown_field: 42"))
    }

    @Test
    fun `убранные время, вложение, список и теги доезжают до другого устройства`() = runBlocking {
        val listId = repo.saveList(null, "Дом", com.sonnik.pin.engine.ListColor.GREEN)
        val id = repo.create(TaskDraft("Позвонить", day = today(), timeMinutes = 18 * 60, tags = listOf("дом")))
        repo.setList(id, listId)
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repoB = PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            repoB.importFiles(repo.exportFiles(includeDeleted = true).map { it.path to it.text })
            assertEquals(18 * 60, repoB.get(id)!!.timeMinutes)
            now = now.plusMinutes(5)
            repo.update(id, TaskDraft("Позвонить", day = today()))
            repo.setList(id, null)
            repoB.importFiles(repo.exportFiles(includeDeleted = true).map { it.path to it.text })
            val e = repoB.get(id)!!
            assertNull(e.timeMinutes)
            assertNull(e.listId)
            assertEquals("", e.tags)
        } finally {
            other.close()
        }
    }

    @Test
    fun `своя категория человека сохраняется и переживает экспорт`() = runBlocking {
        val id = repo.savePerson(null, "Олег", "Крёстный", "", null, emptyList())
        val husband = repo.savePerson(null, "Саша", "муж", "", null, emptyList())
        assertEquals("крёстный", db.people().get(id)!!.relation)
        assertEquals("HUSBAND", db.people().get(husband)!!.relation)
        val files = repo.exportFiles()
        assertTrue(files.single { it.path == "people/Олег.md" }.text.contains("relation: крёстный"))
        assertTrue(files.single { it.path == "people/Саша.md" }.text.contains("relation: husband"))
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }.importFiles(files.map { it.path to it.text })
            assertEquals("крёстный", other.people().get(id)!!.relation)
            assertEquals("HUSBAND", other.people().get(husband)!!.relation)
        } finally {
            other.close()
        }
    }

    @Test
    fun `разбор дня — оставить, отложить, в планы, пропустить повтор`() = runBlocking {
        val day0 = today()
        val a = repo.create(TaskDraft("Хвост", day = day0))
        val b = repo.create(TaskDraft("Потом", day = day0))
        val c = repo.create(TaskDraft("Когда-нибудь", day = day0, timeMinutes = 600))
        val d = repo.create(TaskDraft("Зарядка", day = day0, repeat = RepeatRule.Daily))
        now = now.plusDays(1)
        repo.rollover()
        val today = today()
        assertEquals(1, repo.get(a)!!.rollovers)

        repo.reschedule(a, today)
        assertEquals(0, repo.get(a)!!.rollovers)
        assertEquals(today, repo.get(a)!!.originalDay)

        repo.reschedule(b, today + 3)
        assertEquals(today + 3, repo.get(b)!!.dueDay)

        repo.moveToPlans(c)
        val plan = repo.get(c)!!
        assertEquals(EntryType.PLAN, plan.entryType)
        assertNull(plan.dueDay)
        assertEquals("someday", plan.planScope)

        // Ежедневная уже встала на сегодня сама; пропуск — на завтра и пропуск в журнале.
        repo.skipOccurrence(d)
        assertEquals(today + 1, repo.get(d)!!.dueDay)
        assertTrue(db.journal().all().any { it.entryId == d && it.event == JournalEvent.MISSED && it.day == today })
    }

    @Test
    fun `напоминание заранее сохраняется и уходит в файл, отложенное — нет`() = runBlocking {
        val id = repo.create(TaskDraft("Созвон", day = today(), timeMinutes = 18 * 60, remindBefore = 15))
        repo.snooze(id, 123_000L)
        val e = repo.get(id)!!
        assertEquals(15, e.remindBefore)
        assertEquals(123_000L, e.snoozeUntil)
        val text = repo.exportFiles().single { it.text.contains(id) }.text
        assertTrue(text.contains("remind_before: 15"))
        assertFalse(text.contains("snooze"))
        // Правка сбрасывает отложенное.
        repo.update(id, TaskDraft("Созвон", day = today(), timeMinutes = 18 * 60, remindBefore = 30))
        assertNull(repo.get(id)!!.snoozeUntil)
        assertEquals(30, repo.get(id)!!.remindBefore)
    }

    @Test
    fun `важные даты — сохраняются, не становятся задачами и доезжают через файлы`() = runBlocking {
        val day = java.time.LocalDate.of(2026, 11, 10).toEpochDay()
        val award = repo.saveMoment(null, "Пришла награда", "Грамота за проект", day, null, listOf("работа"))
        val vacation = repo.saveMoment(null, "Отпуск", "", day, com.sonnik.pin.engine.Moments.endOf(day, 17), emptyList())
        repo.rollover()
        val v = repo.get(vacation)!!
        assertEquals(EntryType.MOMENT, v.entryType)
        assertEquals(day + 16, v.momentEnd)
        assertNull(v.dueDay)
        // Не задача: без дня задачи, значит, нет на «Сегодня», нет переноса и напоминаний.
        assertEquals(0, v.rollovers)
        assertNull(repo.get(award)!!.momentEnd)

        val files = repo.exportFiles()
        val file = files.single { it.text.contains(vacation) }
        assertEquals("moments/2026-11-10 Отпуск.md", file.path)
        assertTrue(file.text.contains("type: moment"))
        assertTrue(file.text.contains("date: 2026-11-10"))
        assertTrue(file.text.contains("end: 2026-11-26"))
        assertTrue(!file.text.contains("priority:"))

        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo2 = PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            repo2.importFiles(files.map { it.path to it.text })
            assertEquals(v.copy(updatedAt = 0, createdAt = 0), other.entries().get(vacation)!!.copy(updatedAt = 0, createdAt = 0))
            assertEquals("Грамота за проект", other.entries().get(award)!!.body)
            // Период убрали — стал одной датой; это тоже доезжает (правка позже — новее).
            now = now.plusMinutes(5)
            repo.saveMoment(vacation, "Отпуск", "", day, null, emptyList())
            repo2.importFiles(repo.exportFiles().map { it.path to it.text })
            assertNull(other.entries().get(vacation)!!.momentEnd)
        } finally {
            other.close()
        }
    }

    @Test
    fun `несколько напоминаний сохраняются и доезжают через файлы`() = runBlocking {
        val id = repo.create(TaskDraft("Созвон", day = today(), timeMinutes = 18 * 60, reminders = listOf(120, 24 * 60)))
        val e = repo.get(id)!!
        assertEquals(listOf(120, 24 * 60), e.reminderList)
        assertEquals(120, e.remindBefore)
        val file = repo.exportFiles().single { it.text.contains(id) }
        assertTrue(file.text.contains("reminders: [120, 1440]"))
        val doc = com.sonnik.pin.engine.MdDoc.parse(file.text)
        val back = MdFiles.docToEntry(doc, null, emptyList(), zone, 0L)!!
        assertEquals(listOf(120, 24 * 60), back.reminderList)
        assertEquals(listOf(120, 24 * 60), back.toSnapshot()!!.reminders)
        // Убрали все строки — «не напоминать» тоже доезжает.
        repo.update(id, TaskDraft("Созвон", day = today(), timeMinutes = 18 * 60, reminders = emptyList()))
        val none = repo.exportFiles().single { it.text.contains(id) }
        assertTrue(none.text.contains("reminders: []"))
        assertEquals(emptyList<Int>(), MdFiles.docToEntry(com.sonnik.pin.engine.MdDoc.parse(none.text), e, emptyList(), zone, 0L)!!.reminderList)
        // Файл старой версии (без reminders): одно напоминание за remind_before.
        val old = file.text.lines().filterNot { it.startsWith("reminders:") }.joinToString("\n").replace("remind_before: 120", "remind_before: 30")
        assertEquals(listOf(30), MdFiles.docToEntry(com.sonnik.pin.engine.MdDoc.parse(old), e, emptyList(), zone, 0L)!!.reminderList)
    }

    @Test
    fun `привычки — отметка, снятие, сводка по дням и экспорт`() = runBlocking {
        val today = today()
        val id = repo.saveHabit(null, "Зарядка", null)
        repo.toggleHabit(id, today)
        repo.toggleHabit(id, today + 1) // будущее — не отмечается
        assertEquals(setOf(today), repo.habitDone.first()[id])
        assertEquals(listOf(Triple(today, 1, 1)), repo.habitDays(today, today))
        // Задачи и статистика задач привычки не видят.
        assertTrue(repo.dayStats(today, today).single().done == 0)
        assertTrue(repo.exportFiles().any { it.path.startsWith("habits/") })
        repo.toggleHabit(id, today)
        assertTrue(repo.habitDone.first()[id].isNullOrEmpty())
    }

    @Test
    fun `минуты фокуса пишутся в журнал и не ломают статистику задач`() = runBlocking {
        val day = today()
        val id = repo.create(TaskDraft("Отчёт", day = day))
        repo.logFocus(id, 25, repo.now())
        repo.logFocus(null, 12, repo.now())
        assertEquals(listOf(Triple(day, 37, 2)), repo.focusDays(day, day))
        assertEquals(0, repo.dayStats(day, day).single().done)
        assertTrue(repo.exportFiles().single { it.path.startsWith("journal/") }.text.contains("| FOCUS |"))
    }

    @Test
    fun `списки — создание из «@», файлы и удаление без потери задач`() = runBlocking {
        val day = today()
        val home = repo.listIdFor("дом")
        assertEquals(home, repo.listIdFor("Дом"))
        assertEquals("Дом", db.lists().get(home)!!.name)
        val work = repo.saveList(null, "Работа", com.sonnik.pin.engine.ListColor.RED)
        val a = repo.create(TaskDraft("Полить цветы", day = day, listId = home))
        repo.create(TaskDraft("Отчёт", day = day, listId = work))
        val files = repo.exportFiles()
        assertTrue(files.any { it.path == "lists/Дом.md" && it.text.contains("type: list") })
        assertTrue(files.any { it.path.startsWith("tasks/") && it.text.contains("list: Дом") && it.text.contains("list_id: $home") })

        // Импорт в пустую базу восстанавливает списки и связи.
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo2 = PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            repo2.importFiles(files.map { it.path to it.text })
            assertEquals(setOf("Дом", "Работа"), other.lists().allWithDeleted().map { it.name }.toSet())
            assertEquals(home, other.entries().get(a)!!.listId)
        } finally {
            other.close()
        }

        // Файл, где список указан только именем.
        repo.importFiles(listOf("tasks/new.md" to "---\ntitle: Купить лампочку\nlist: работа\n---\n"))
        assertEquals(work, db.entries().all().single { it.title == "Купить лампочку" }.listId)

        repo.deleteList(home)
        assertNull(db.entries().get(a)!!.listId)
        assertTrue(db.lists().get(home)!!.deleted)
        assertEquals(listOf("Работа"), repo.lists.first().map { it.name })
    }

    @Test
    fun `план в задачу сразу со временем, подпункты отмечаются из списка`() = runBlocking {
        val day = today()
        val plan = repo.savePlan(null, "Съездить на шиномонтаж", "- [ ] записаться\n- [ ] поехать", PlanScope.Someday, emptyList())
        repo.planToTask(plan, day + 1, 18 * 60)
        val task = db.entries().get(plan)!!
        assertEquals(EntryType.ONE_TIME, task.entryType)
        assertEquals(day + 1, task.dueDay)
        assertEquals(18 * 60, task.timeMinutes)
        repo.setBody(plan, com.sonnik.pin.engine.Markdown.toggleTask(task.body, 0))
        assertEquals("- [x] записаться\n- [ ] поехать", db.entries().get(plan)!!.body)
    }

    @Test
    fun `напоминание привычки — время сохраняется, отметка из уведомления не снимает уже отмеченное`() = runBlocking {
        val day = today()
        val id = repo.saveHabit(null, "Зарядка", null, 7 * 60)
        repo.saveHabit(null, "Без напоминания", null)
        assertEquals(listOf("Зарядка" to 7 * 60), repo.habitAlarms().map { it.title to it.timeMinutes })
        repo.markHabit(id, day)
        repo.markHabit(id, day)
        assertEquals(setOf(day), repo.habitAlarms().single().habit.doneDays)
        assertTrue(repo.exportFiles().single { it.path.startsWith("habits/Зарядка") }.text.contains("time: \"07:00\""))
    }

    @Test
    fun `своё событие, вернувшееся из файла, не задваивается, а старые двойники убираются`() = runBlocking {
        now = now.withNano(437_000_000) // миллисекунды есть в базе, но не в файле
        val id = repo.create(TaskDraft("Записаться на даунсайз", day = today()))
        repo.setDone(id, true)
        val files = repo.exportFiles()
        val report = repo.importFiles(files.map { it.path to it.text })
        assertEquals(0, report.journal)
        assertEquals(1, db.journal().all().count { it.entryId == id && it.event == "DONE" })

        // Двойник из старых версий (то же событие без миллисекунд) — убирается.
        val original = db.journal().all().single { it.entryId == id && it.event == "DONE" }
        db.journal().insertAll(listOf(original.copy(id = 0, at = original.at / 1000 * 1000)))
        assertEquals(1, repo.dedupeJournal())
        assertEquals(1, db.journal().all().count { it.entryId == id && it.event == "DONE" })
        assertEquals(0, repo.dedupeJournal())
    }

    @Test
    fun `поделиться людьми и добавить их на другом телефоне`() = runBlocking {
        val anya = repo.savePerson(null, "Аня", Relation.WIFE.name, "любит кофе", PinRepository.Birthday(11, 10, 1997), listOf(7, 1))
        val mama = repo.savePerson(null, "Мама", Relation.FAMILY.name, "", PinRepository.Birthday(3, 4, null), listOf(3))
        repo.savePerson(null, "Не делиться", Relation.FRIEND.name, "", null, emptyList())
        val vcf = com.sonnik.pin.engine.VCard.write(repo.sharedPeople(listOf(anya, mama)))

        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo2 = PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            repo2.savePerson(null, "мама", Relation.FAMILY.name, "своя заметка", null, emptyList())
            assertEquals(2, repo2.importPeople(com.sonnik.pin.engine.VCard.parse(vcf)))
            assertEquals(setOf("Аня", "мама"), other.people().allWithDeleted().map { it.name }.toSet())
            assertEquals("своя заметка", other.people().allWithDeleted().single { it.name == "мама" }.note)
            val birthdays = other.entries().all().filter { it.entryType == EntryType.BIRTHDAY }
            assertEquals(setOf("yearly 11-10", "yearly 03-04"), birthdays.map { it.repeatRule }.toSet())
            assertEquals(1997, birthdays.single { it.repeatRule == "yearly 11-10" }.birthYear)
            // Повторный импорт ничего не добавляет.
            assertEquals(0, repo2.importPeople(com.sonnik.pin.engine.VCard.parse(vcf)))
        } finally {
            other.close()
        }
    }

    @Test
    fun `вложения едут через архив на другой телефон`() = runBlocking {
        val dirA = java.nio.file.Files.createTempDirectory("pin-a").toFile()
        val dirB = java.nio.file.Files.createTempDirectory("pin-b").toFile()
        val repoA = PinRepository(db, attachmentsDir = dirA) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
        val id = repoA.create(TaskDraft("Оплатить счёт", day = today()))
        val a = repoA.addAttachment(id, "Счёт за май.pdf", byteArrayOf(1, 2, 3))
        assertEquals(listOf(a), com.sonnik.pin.engine.Attachments.parse(db.entries().get(id)!!.attachments))
        val zip = Archive.toBytes(repoA.exportFiles(includeDeleted = true), repoA.exportBlobs())

        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repoB = PinRepository(other, attachmentsDir = dirB) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            val content = Archive.readContent(java.io.ByteArrayInputStream(zip))
            assertEquals(1, repoB.importBlobs(content.blobs))
            repoB.importFiles(content.files)
            val got = com.sonnik.pin.engine.Attachments.parse(other.entries().get(id)!!.attachments).single()
            assertEquals("Счёт за май.pdf", got.name)
            assertTrue(repoB.attachmentFile(got)!!.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        } finally {
            other.close()
        }
        repoA.removeAttachment(id, a.id)
        assertNull(db.entries().get(id)!!.attachments)
        assertTrue(!repoA.attachmentFile(a)!!.exists())
    }

    @Test
    fun `ночной перенос не затирает правку с другого устройства`() = runBlocking {
        val id = repo.create(TaskDraft("Старое имя", day = today() - 2))
        val before = repo.get(id)!!.updatedAt
        repo.rollover()
        // Перенос — расчёт, а не правка: время изменения прежнее.
        assertEquals(before, repo.get(id)!!.updatedAt)
        // Вечерняя правка с компьютера (новее) после переноса всё равно принимается.
        val desktop = repo.get(id)!!.copy(title = "Новое имя", updatedAt = before + 60_000)
        val files = MdFiles.export(listOf(desktop), emptyList(), emptyList(), zone, includeDeleted = true)
        repo.importFiles(files.map { it.path to it.text })
        assertEquals("Новое имя", repo.get(id)!!.title)
        assertEquals(today(), repo.get(id)!!.dueDay)
    }

    @Test
    fun `снятая отметка не возвращается с другого устройства`() = runBlocking {
        val id = repo.saveHabit(null, "Зарядка", null)
        repo.toggleHabit(id, today())
        // Файл «другого телефона» — с этой отметкой.
        val withMark = repo.exportFiles(includeDeleted = true)
        repo.toggleHabit(id, today())
        assertTrue(repo.habitDone.first()[id].isNullOrEmpty())
        repo.importFiles(withMark.map { it.path to it.text })
        assertTrue(repo.habitDone.first()[id].isNullOrEmpty())
        // А «надгробие», пришедшее с другого устройства, снимает отметку и здесь.
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repoB = PinRepository(other) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            repoB.importFiles(withMark.map { it.path to it.text })
            assertEquals(setOf(today()), repoB.habitDone.first()[id])
            repoB.importFiles(repo.exportFiles(includeDeleted = true).map { it.path to it.text })
            assertTrue(repoB.habitDone.first()[id].isNullOrEmpty())
        } finally {
            other.close()
        }
    }

    @Test
    fun `граница дня берётся из настроек`() = runBlocking {
        now = LocalDateTime.of(2026, 11, 3, 2, 0)
        val withDefault = today()
        repo.setDayBoundary(0)
        assertEquals(withDefault + 1, today())
    }
}
