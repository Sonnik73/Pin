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
        assertTrue(db.journal().between(day0, day0).isEmpty())
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
        val id = repo.savePerson(null, "Аня", Relation.WIFE, "", PinRepository.Birthday(11, 9, 1997), listOf(7, 1))
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
        assertEquals(9, holidays.size)
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
        val pid = repo.savePerson(null, "Аня", Relation.WIFE, "любит кофе", PinRepository.Birthday(11, 9, 1997), listOf(7, 1))
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
    fun `граница дня берётся из настроек`() = runBlocking {
        now = LocalDateTime.of(2026, 11, 3, 2, 0)
        val withDefault = today()
        repo.setDayBoundary(0)
        assertEquals(withDefault + 1, today())
    }
}
