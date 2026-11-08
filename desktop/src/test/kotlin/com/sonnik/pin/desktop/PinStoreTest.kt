package com.sonnik.pin.desktop

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Работа с общим файлом: чтение, запись, работа без связи и возвращение файла. */
class PinStoreTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 8).toEpochDay()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val root = java.nio.file.Files.createTempDirectory("pin-store").toFile()
    private val share = File(root, "share")
    private val file = File(share, "Pin.zip")
    private val local = LocalCopy(File(root, "local"))

    @After
    fun cleanup() {
        scope.cancel()
        root.deleteRecursively()
    }

    private fun store() = PinStore(scope, zone, local, file.path) {}

    private suspend fun waitUntil(what: String, condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(25)
        }
        throw AssertionError("не дождались: $what")
    }

    private fun onDisk(): PinData = PinData.read(file.readBytes(), zone)

    @Test
    fun `правка ложится в файл`() = runBlocking {
        share.mkdirs()
        file.writeBytes(Edits.toBytes(PinData.EMPTY, zone))
        val s = store()
        s.tick()
        waitUntil("файл прочитан") { s.loaded?.data != null }
        s.commit { Edits.create(it, Draft("Купить хлеб", day = today), 1L).first }
        waitUntil("правка записана") { !s.unsaved && runCatching { onDisk().entries.size == 1 }.getOrDefault(false) }
        assertEquals("Купить хлеб", onDisk().entries.single().title)
    }

    @Test
    fun `без связи с файлом работа продолжается, правки уезжают, когда файл вернулся`() = runBlocking {
        share.mkdirs()
        file.writeBytes(Edits.toBytes(PinData.EMPTY, zone))
        val s = store()
        s.tick()
        waitUntil("файл прочитан") { s.loaded?.data != null }

        // «Выключили компьютер с общей папкой».
        val backup = File(root, "share-off")
        assertTrue("папка «выключена»", share.renameTo(backup))
        s.tick()
        assertTrue("нет связи — тихая отметка", s.offline)
        assertTrue("задачи на экране остались", s.loaded?.data != null)
        s.commit { Edits.create(it, Draft("Позвонить", day = today), 2L).first }
        // Правка видна сразу, а копия на компьютере пишется следом — ждём её.
        waitUntil("копия на компьютере") { local.load(file.path, zone)?.let { it.unsaved && it.data.entries.size == 1 } == true }
        assertTrue("правка ждёт записи", s.unsaved)
        assertEquals(null, s.problem)

        // Программу закрыли и открыли снова, файла всё ещё нет: копия на компьютере.
        val restarted = store()
        assertEquals("после перезапуска задачи на месте", 1, restarted.loaded?.data?.entries?.size)
        assertTrue("после перезапуска правка ждёт записи", restarted.unsaved)

        // Пока нас не было, другой компьютер добавил свою задачу; папка вернулась.
        val other = Edits.create(PinData.read(File(backup, "Pin.zip").readBytes(), zone), Draft("С другого компьютера", day = today), 3L).first
        File(backup, "Pin.zip").writeBytes(Edits.toBytes(other, zone))
        assertTrue("папка вернулась", backup.renameTo(share))
        restarted.tick()
        waitUntil("правки уехали") { !restarted.unsaved }
        assertEquals(setOf("Позвонить", "С другого компьютера"), onDisk().entries.map { it.title }.toSet())
        assertFalse("связь восстановлена", restarted.offline)
    }

    @Test
    fun `испорченный файл заменяется данными компьютера`() = runBlocking {
        share.mkdirs()
        file.writeBytes(Edits.toBytes(Edits.create(PinData.EMPTY, Draft("Важное", day = today), 1L).first, zone))
        val s = store()
        s.tick()
        waitUntil("файл прочитан") { s.loaded?.data?.entries?.size == 1 }
        val good = file.readBytes()
        file.writeBytes(good.copyOf(good.size / 2))
        repeat(25) { s.tick() }
        waitUntil("файл починен") { !s.unsaved && runCatching { onDisk().entries.size == 1 }.getOrDefault(false) }
        assertEquals("Важное", onDisk().entries.single().title)
    }
}
