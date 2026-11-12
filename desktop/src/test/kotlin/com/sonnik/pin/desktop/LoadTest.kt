package com.sonnik.pin.desktop

import com.sonnik.pin.data.LoadData
import com.sonnik.pin.data.LoadReport
import com.sonnik.pin.engine.Reminders
import com.sonnik.pin.engine.Search
import com.sonnik.pin.engine.SearchFilter
import com.sonnik.pin.engine.SearchItem
import com.sonnik.pin.engine.TaskStatus
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Проверка нагрузкой Pin для Windows: тот же Pin.zip «за пять лет», что и у телефона.
 * Только с PIN_LOAD=1; отчёт — desktop/build/load-report.md.
 */
class LoadTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 12).toEpochDay()
    private val now = 1_794_474_000_000L // 12.11.2026 12:00 МСК

    @Test
    fun `Pin для Windows — пять лет данных`() {
        assumeTrue(System.getenv("PIN_LOAD") == "1")
        val report = LoadReport("Pin для Windows")
        val gen = LoadData.generate(today, now)
        report.line("Данные: ${gen.describe()}")
        val start = PinData(gen.entries, gen.people, gen.lists, gen.journal, emptyMap())

        lateinit var zip: ByteArray
        report.measure("Запись Pin.zip", 20_000) { zip = Edits.toBytes(start, zone) }
        report.line("Pin.zip: ${zip.size / 1024} КБ")
        lateinit var data: PinData
        report.measure("Открытие Pin.zip (чтение и разбор)", 30_000) { data = PinData.read(zip, zone, now) }
        assertEquals(gen.entries.size, data.entries.size)

        // Компьютер не открывали две недели — перенос досчитывается при показе.
        val later = today + 14
        report.measure("Экран «Сегодня»", 3_000) {
            data.board(later)
            data.habitsFor(later)
            data.upcomingEvents(later)
        }
        report.measure("Календарь: месяц (значки, события)", 5_000) {
            data.monthMarks(later - 15, later + 20, later)
            data.monthEvents(later - 15, later + 20)
        }
        report.measure("Календарь: перелистать 12 месяцев", 20_000) {
            for (m in 0 until 12) {
                val from = later + m * 30
                data.monthMarks(from, from + 41, later)
                data.monthEvents(from, from + 41)
            }
        }
        report.measure("Проверка напоминаний (раз в 30 с)", 2_000) {
            Reminders.due(data.openSnapshots(later), now - 30_000, now, zone, 240)
            data.occasionTexts(later)
        }
        val items = data.entries.map { e -> SearchItem(e.id, e.entryType, e.title, e.body, e.tagList, e.entryPriority, e.entryStatus, e.dueDay ?: e.momentDay, "", e.completedDay, e.updatedAt) }
        report.measure("Поиск: 12 нажатий клавиш", 6_000) {
            val q = "позвонить 42"
            for (i in 1..q.length) Search.filter(items, SearchFilter(query = q.take(i)), later)
        }

        val open = data.entries.first { it.entryStatus == TaskStatus.OPEN && it.id.startsWith("open") }
        var edited = data
        report.measure("Отметить задачу и снять ×20 (в памяти)", 10_000) {
            repeat(10) {
                edited = Edits.setDone(edited, open.id, true, later, now)
                edited = Edits.setDone(edited, open.id, false, later, now)
            }
        }
        report.measure("Отметить привычку ×20 (в памяти)", 10_000) {
            repeat(20) { edited = Edits.toggleHabit(edited, "habit1", later - it, later, now) }
        }
        report.measure("Новая задача ×20 (в памяти)", 10_000) {
            repeat(20) { edited = Edits.create(edited, Draft("Нагрузка $it", day = later), now).first }
        }
        // Одно сохранение: перечитать файл, слить свои правки и записать.
        report.measure("Сохранение правки в Pin.zip (чтение + слияние + запись)", 30_000) {
            val onDisk = PinData.read(zip, zone, now)
            val merged = Edits.merge(edited, onDisk)
            zip = Edits.toBytes(merged, zone)
        }
        report.measure("Сохранение, когда файл не менялся (без чтения)", 30_000) {
            val merged = Edits.merge(Edits.setDone(edited, open.id, true, later, now + 1), data)
            zip = Edits.toBytes(merged, zone)
        }
        report.measure("Повторное открытие после сохранения", 30_000) { data = PinData.read(zip, zone, now) }
        assertEquals(edited.entries.size, data.entries.size)

        report.write(File("build/load-report.md"))
        report.assertWithinLimits()
    }
}
