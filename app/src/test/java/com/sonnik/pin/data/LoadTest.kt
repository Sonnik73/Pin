package com.sonnik.pin.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sonnik.pin.data.PinRepository.Companion.toStatEvent
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Moments
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.Search
import com.sonnik.pin.engine.SearchFilter
import com.sonnik.pin.engine.SearchItem
import com.sonnik.pin.engine.Stats
import com.sonnik.pin.engine.TaskStatus
import com.sonnik.pin.engine.TodayBoard
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверка нагрузкой телефона: база «за пять лет активной работы» и все тяжёлые операции над ней.
 * Запускается только с PIN_LOAD=1 (вручную, workflow «Load test»); отчёт — app/build/load-report.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoadTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private var now = LocalDateTime.of(2026, 11, 12, 12, 0)
    private lateinit var db: PinDatabase
    private lateinit var repo: PinRepository
    private val report = LoadReport("Телефон (Room, Robolectric)")

    @Before
    fun setUp() {
        assumeTrue("проверка нагрузкой — только с PIN_LOAD=1", System.getenv("PIN_LOAD") == "1")
        db = newDb()
        repo = PinRepository(db) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
    }

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
        .allowMainThreadQueries().build()

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `пять лет данных — все тяжёлые операции укладываются в лимиты`() = runBlocking {
        val today = repo.logicalDay().today()
        val gen = LoadData.generate(today, now.atZone(zone).toInstant().toEpochMilli())
        report.line("Данные: ${gen.describe()}")

        report.measure("Запись в базу (первичная)", 60_000) {
            db.entries().upsertAll(gen.entries)
            gen.people.forEach { db.people().upsert(it) }
            gen.lists.forEach { db.lists().upsert(it) }
            db.journal().insertAll(gen.journal)
        }
        repo.updateSettingsQuietly { it.copy(lastRolloverDay = today) }

        // Телефон пролежал выключенным две недели: перенос досчитывается за все ночи.
        now = now.plusDays(14)
        val later = today + 14
        var moved = 0
        report.measure("Ночной перенос за 14 пропущенных ночей", 20_000) { moved = repo.rollover() }
        report.line("Перенесено записей: $moved, событий в журнале теперь: ${db.journal().all().size}")
        assertTrue(moved > 0)
        report.measure("Повторный перенос (ничего не делает)", 2_000) { assertEquals(0, repo.rollover()) }

        report.measure("Экран «Сегодня» (TodayBoard)", 3_000) {
            val open = repo.openDated().mapNotNull { it.toSnapshot() }
            TodayBoard.build(open, later, { null })
        }
        report.measure("Календарь: значки месяца", 3_000) { repo.monthMarks(later - 15, later + 15) }
        report.measure("Календарь: события месяца", 3_000) { repo.monthEvents(later - 15, later + 15) }
        report.measure("Статистика за год по дням", 5_000) { repo.dayStats(later - 365, later) }
        report.measure("Статистика за 5 лет (сводка, теги, серии)", 10_000) {
            val events = db.journal().between(later - 5 * 365, later).map { it.toStatEvent() }
            val tags = db.entries().all().associate { it.id to it.tagList }
            Stats.summary(events, later - 5 * 365, later, later, { tags[it].orEmpty() })
        }
        report.measure("Привычки по дням для Крепости (год)", 5_000) { repo.habitDays(later - 365, later) }
        report.measure("Важные даты: итоги групп", 2_000) {
            Moments.stats(db.entries().all().mapNotNull { it.toMoment() }, later)
        }
        report.measure("Напоминания дня (дни рождения, важные даты)", 2_000) {
            repo.occasionNotices(later)
            repo.momentNotices(later)
        }

        val all = db.entries().all()
        val items = all.map { e -> SearchItem(e.id, e.entryType, e.title, e.body, e.tagList, e.entryPriority, e.entryStatus, e.dueDay ?: e.momentDay, "", e.completedDay, e.updatedAt) }
        report.measure("Поиск: 12 нажатий клавиш по ${items.size} записям", 6_000) {
            val q = "позвонить 42"
            for (i in 1..q.length) Search.filter(items, SearchFilter(query = q.take(i)), later)
        }
        report.measure("Поиск по тегу и «важным»", 2_000) {
            Search.filter(items, SearchFilter(tag = "дом", importantOnly = true), later)
        }

        val first = all.first { it.entryStatus == TaskStatus.OPEN && it.entryType == EntryType.ONE_TIME }
        report.measure("Отметить задачу и снять отметку ×20", 5_000) {
            repeat(10) {
                repo.setDone(first.id, true)
                repo.setDone(first.id, false)
            }
        }
        report.measure("Новая задача ×50", 5_000) {
            repeat(50) { repo.create(TaskDraft("Нагрузка $it", day = later, priority = Priority.HIGH)) }
        }

        lateinit var files: List<MdFiles.File>
        lateinit var zip: ByteArray
        report.measure("Экспорт в .md (все файлы)", 20_000) { files = repo.exportFiles(includeDeleted = true) }
        report.measure("Экспорт в .md повторно (тексты из кэша)", 10_000) { files = repo.exportFiles(includeDeleted = true) }
        report.measure("Упаковка Pin.zip", 20_000) { zip = Archive.toBytes(files) }
        report.line("Файлов: ${files.size}, Pin.zip: ${zip.size / 1024} КБ, текст: ${files.sumOf { it.text.length } / 1024} КБ")
        lateinit var content: Archive.Content
        report.measure("Распаковка Pin.zip", 20_000) { content = Archive.readContent(ByteArrayInputStream(zip)) }
        assertEquals(files.size, content.files.size)
        report.measure("Синхронизация без изменений (импорт того же)", 60_000) {
            val r = repo.importFiles(content.files)
            assertEquals(0, r.added)
        }

        // Синхронизация с файлом, как в фоне: первая сверяет всё, следующая без изменений — по отпечатку.
        var shared: ByteArray? = zip
        report.measure("Синхронизация с Pin.zip (полная)", 60_000) {
            com.sonnik.pin.sync.FileSyncEngine.sync(repo, read = { shared }, write = { shared = it })
        }
        report.measure("Синхронизация с Pin.zip — ничего не менялось", 3_000) {
            val r = com.sonnik.pin.sync.FileSyncEngine.sync(repo, read = { shared }, write = { shared = it })
            assertEquals(null, r.imported)
            assertEquals(false, r.written)
        }
        report.measure("Синхронизация после одной правки", 60_000) {
            repo.setDone(first.id, true)
            val r = com.sonnik.pin.sync.FileSyncEngine.sync(repo, read = { shared }, write = { shared = it })
            assertEquals(true, r.written)
        }

        // Новый телефон: всё приезжает из файла в пустую базу.
        val fresh = newDb()
        try {
            val repo2 = PinRepository(fresh) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
            report.measure("Первая синхронизация в пустую базу", 120_000) {
                val r = repo2.importFiles(content.files)
                report.line("Новый телефон: ${r.describe()}")
            }
            assertEquals(db.entries().allWithDeleted().size, fresh.entries().allWithDeleted().size)
        } finally {
            fresh.close()
        }
        report.write(File("build/load-report.md"))
        report.assertWithinLimits()
    }
}
