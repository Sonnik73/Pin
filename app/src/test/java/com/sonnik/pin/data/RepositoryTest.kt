package com.sonnik.pin.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sonnik.pin.engine.Priority
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
    fun `граница дня берётся из настроек`() = runBlocking {
        now = LocalDateTime.of(2026, 11, 3, 2, 0)
        val withDefault = today()
        repo.setDayBoundary(0)
        assertEquals(withDefault + 1, today())
    }
}
