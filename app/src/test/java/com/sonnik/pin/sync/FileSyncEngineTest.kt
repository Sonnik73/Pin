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

    private fun device(withFiles: Boolean = false): PinRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        dbs += db
        val dir = if (withFiles) java.nio.file.Files.createTempDirectory("pin-att").toFile() else null
        return PinRepository(db, attachmentsDir = dir) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
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

    @Test
    fun `оборванный файл чинится данными телефона`() = runBlocking {
        val a = device()
        a.create(TaskDraft("Задача", day = a.logicalDay().today()))
        sync(a)
        val good = file!!
        // Запись оборвалась на середине: половина архива.
        file = good.copyOf(good.size / 2)
        val report = sync(a)
        assertTrue(report.written)
        assertTrue(file!!.contentEquals(good))
    }

    @Test
    fun `вложения идут потоком, снятые — убираются на другом телефоне`() = runBlocking {
        val a = device(withFiles = true)
        val b = device(withFiles = true)
        val id = a.create(TaskDraft("Счёт", day = a.logicalDay().today()))
        val att = a.addAttachment(id, "Счёт.pdf", ByteArray(300_000) { (it % 251).toByte() })
        sync(a)
        sync(b)
        assertEquals(300_000L, b.attachmentFile(att)!!.length())
        now = now.plusMinutes(1)
        a.removeAttachment(id, att.id)
        sync(a)
        sync(b)
        assertFalse(b.attachmentFile(att)!!.exists())
    }

    @Test
    fun `вложений больше 50 МБ не прикрепить`() = runBlocking {
        val a = device(withFiles = true)
        val id = a.create(TaskDraft("Много файлов", day = a.logicalDay().today()))
        repeat(5) { a.addAttachment(id, "файл$it.bin", ByteArray(10 * 1024 * 1024)) }
        val r = runCatching { a.addAttachment(id, "ещё.bin", ByteArray(1024 * 1024)) }
        assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("50 МБ"))
    }
}
