package com.sonnik.pin.desktop

import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.EntryType
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class PinFileIoTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 7).toEpochDay()

    @Test
    fun `два компьютера пишут в один файл одновременно — ни одна правка не теряется`() {
        val file = java.nio.file.Files.createTempFile("pin", ".zip").toFile()
        file.writeBytes(Edits.toBytes(PinData.EMPTY, zone))
        val workers = (1..2).map { pc ->
            thread {
                repeat(15) { i ->
                    val id = "pc$pc-$i"
                    while (true) {
                        try {
                            PinFileIo.update(file) { bytes ->
                                val onDisk = PinData.read(bytes, zone)
                                val e = EntryEntity(id = id, type = EntryType.ONE_TIME.name, title = id, dueDay = today, originalDay = today, createdAt = 1L, updatedAt = 1L)
                                val merged = Edits.merge(PinData(onDisk.entries + e, onDisk.people, onDisk.lists, onDisk.journal, onDisk.blobs), onDisk)
                                Unit to Edits.toBytes(merged, zone)
                            }
                            break
                        } catch (_: FileBusyException) {
                            Thread.sleep(5)
                        }
                    }
                }
            }
        }
        workers.forEach { it.join() }
        val result = PinData.read(PinFileIo.readLocked(file), zone)
        assertEquals(30, result.entries.size)
    }

    @Test
    fun `копия на компьютере — своя у каждого файла, с несохранёнными правками и вложениями`() {
        val dir = java.nio.file.Files.createTempDirectory("pin-local").toFile()
        val copy = LocalCopy(dir)
        val (data, id) = Edits.create(PinData.EMPTY, Draft("Позвонить", day = today), 1L)
        val withFile = Edits.addAttachment(data, id, "Счёт.pdf", byteArrayOf(1, 2, 3), 2L)
        copy.save("\\\\ПК1\\Pin\\Pin.zip", withFile, unsaved = true, zone = zone)
        copy.save("D:\\другой.zip", PinData.EMPTY, unsaved = false, zone = zone)
        val back = copy.load("\\\\ПК1\\Pin\\Pin.zip", zone)!!
        assertEquals("Позвонить", back.data.byId.getValue(id).title)
        assertEquals(true, back.unsaved)
        val a = com.sonnik.pin.engine.Attachments.parse(back.data.byId.getValue(id).attachments).single()
        assertEquals(3, back.data.blob(a)!!.size)
        assertEquals(0, copy.load("D:\\другой.zip", zone)!!.data.entries.size)
        assertEquals(null, copy.load("E:\\никогда.zip", zone))
        // Убрали вложение — его файл из копии тоже уходит.
        copy.save("\\\\ПК1\\Pin\\Pin.zip", Edits.removeAttachment(back.data, id, a.id, 3L), unsaved = false, zone = zone)
        assertEquals(null, copy.load("\\\\ПК1\\Pin\\Pin.zip", zone)!!.data.blob(a))
    }
}
