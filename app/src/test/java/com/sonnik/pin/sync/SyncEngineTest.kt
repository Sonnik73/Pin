package com.sonnik.pin.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sonnik.pin.data.PinDatabase
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.Relation
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Диск в памяти: как настоящий, хранит MD5 и время изменения. */
class FakeDrive(private val clock: () -> Long) : DriveStore {
    val files = LinkedHashMap<String, Pair<RemoteFile, String>>()
    private var next = 0

    override suspend fun listAll() = files.values.map { it.first }
    override suspend fun download(file: RemoteFile) = files.getValue(file.id).second
    override suspend fun create(folder: String, name: String, key: String?, text: String, conflict: Boolean): RemoteFile {
        val f = RemoteFile("f${next++}", name, folder, key, md5Hex(text), clock(), conflict)
        files[f.id] = f to text
        return f
    }
    override suspend fun update(file: RemoteFile, folder: String, name: String, text: String): RemoteFile {
        val f = file.copy(name = name, folder = folder, md5 = md5Hex(text), modified = clock())
        files[f.id] = f to text
        return f
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private var now = LocalDateTime.of(2026, 11, 2, 12, 0)
    private fun millis() = now.atZone(zone).toInstant().toEpochMilli()
    private val dbs = ArrayList<PinDatabase>()

    private fun device(): PinRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PinDatabase::class.java)
            .allowMainThreadQueries().build()
        dbs += db
        return PinRepository(db) { Clock.fixed(now.atZone(zone).toInstant(), zone) }
    }

    @After
    fun close() = dbs.forEach { it.close() }

    private fun tick(minutes: Long = 5) {
        now = now.plusMinutes(minutes)
    }

    @Test
    fun `два телефона через Диск`() = runBlocking {
        val drive = FakeDrive(::millis)
        val a = device()
        val b = device()
        val today = a.logicalDay().today()
        val id = a.create(TaskDraft("Купить хлеб", day = today))
        a.savePerson(null, "Аня", Relation.WIFE.name, "", PinRepository.Birthday(3, 8, null), listOf(7, 1))
        a.setDone(id, true)

        val first = SyncEngine(a, drive, zone).sync()
        assertEquals(4, first.created) // задача, день рождения, человек, журнал
        tick()
        val second = SyncEngine(b, drive, zone).sync()
        assertEquals(4, second.downloaded)
        val diff = b.exportFiles(includeDeleted = true).filter { f ->
            drive.files.values.firstOrNull { it.first.key == f.key }?.second != f.text
        }.joinToString(" || ") { f -> f.path + " LOCAL=" + f.text.replace("\n", "⏎") + " REMOTE=" + drive.files.values.firstOrNull { it.first.key == f.key }?.second?.replace("\n", "⏎") }
        assertEquals("лишняя отправка: $diff", 0, second.created + second.uploaded)
        assertEquals("Купить хлеб", b.get(id)!!.title)
        assertEquals(1, b.exportFiles().count { it.path.startsWith("journal/") })

        // Правка на B доходит до A.
        tick()
        b.update(id, TaskDraft("Купить хлеб и молоко", day = today))
        SyncEngine(b, drive, zone).sync()
        tick()
        val third = SyncEngine(a, drive, zone).sync()
        assertTrue(third.downloaded >= 1)
        assertEquals("Купить хлеб и молоко", a.get(id)!!.title)
        assertEquals("конфликты на A", 0, third.conflicts)

        // Повторная синхронизация ничего не гоняет.
        tick()
        val idle = SyncEngine(a, drive, zone).sync()
        assertEquals(SyncReport(0, 0, 0, 0), idle)

        // Удаление передаётся пометкой.
        tick()
        a.delete(id)
        SyncEngine(a, drive, zone).sync()
        tick()
        SyncEngine(b, drive, zone).sync()
        assertTrue(b.get(id)!!.deleted)
    }

    @Test
    fun `конфликт — побеждает более новая правка, копия проигравшей на Диске`() = runBlocking {
        val drive = FakeDrive(::millis)
        val a = device()
        val b = device()
        val today = a.logicalDay().today()
        val id = a.create(TaskDraft("Отчёт", day = today))
        SyncEngine(a, drive, zone).sync()
        tick()
        SyncEngine(b, drive, zone).sync()

        tick()
        a.update(id, TaskDraft("Отчёт (версия A)", day = today))
        tick()
        b.update(id, TaskDraft("Отчёт (версия B)", day = today))
        tick()
        SyncEngine(b, drive, zone).sync()
        tick()
        val report = SyncEngine(a, drive, zone).sync()
        assertEquals(1, report.conflicts)
        assertEquals("Отчёт (версия B)", a.get(id)!!.title)
        val copy = drive.files.values.single { it.first.conflict }
        assertEquals("conflicts", copy.first.folder)
        assertTrue(copy.second.contains("Отчёт (версия A)"))
        // Копия конфликта не читается обратно.
        tick()
        SyncEngine(b, drive, zone).sync()
        assertEquals("Отчёт (версия B)", b.get(id)!!.title)
    }
}
