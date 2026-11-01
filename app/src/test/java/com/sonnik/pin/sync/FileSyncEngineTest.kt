package com.sonnik.pin.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sonnik.pin.data.PinDatabase
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.data.TaskDraft
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSyncEngineTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private var now = LocalDateTime.of(2026, 11, 2, 12, 0)
    private val dbs = ArrayList<PinDatabase>()

    private fun device(): PinRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        dbs += db
        return PinRepository(db) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
    }

    @After
    fun close() = dbs.forEach { it.close() }

    /** Файл на «Диске» — просто байты. */
    private var file: ByteArray? = null
    private var writes = 0

    private suspend fun sync(repo: PinRepository) = FileSyncEngine.sync(repo, { file }, { file = it; writes++ })

    @Test
    fun `два телефона через один файл`() = runBlocking {
        val a = device()
        val b = device()
        val today = a.logicalDay().today()
        val id = a.create(TaskDraft("Купить хлеб", day = today))
        a.setDone(id, true)

        val first = sync(a)
        assertNull(first.imported)
        assertTrue(first.written)

        now = now.plusMinutes(5)
        val second = sync(b)
        assertEquals(1, second.imported!!.added)
        assertFalse("ничего не изменилось — файл не перезаписывается", second.written)
        assertEquals("Купить хлеб", b.get(id)!!.title)

        // Правка на B → файл → A.
        now = now.plusMinutes(5)
        b.update(id, TaskDraft("Купить хлеб и молоко", day = today))
        assertTrue(sync(b).written)
        now = now.plusMinutes(5)
        sync(a)
        assertEquals("Купить хлеб и молоко", a.get(id)!!.title)

        // Повтор без изменений ничего не пишет.
        val before = writes
        sync(a)
        sync(b)
        assertEquals(before, writes)
    }

    @Test
    fun `пустой или чужой файл не ломает данные`() = runBlocking {
        val a = device()
        a.create(TaskDraft("Задача", day = a.logicalDay().today()))
        file = ByteArray(0)
        assertTrue(sync(a).written)
        file = "не zip".toByteArray()
        val r = runCatching { sync(a) }
        assertTrue(r.isSuccess)
        assertEquals(1, a.exportFiles().count { it.path.startsWith("tasks/") })
    }
}
