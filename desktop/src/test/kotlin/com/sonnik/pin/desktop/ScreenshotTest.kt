package com.sonnik.pin.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.TaskStatus
import java.io.File
import java.time.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Снимки окна программы для Windows (SCREENSHOTS=1, как у телефона) — в docs/screenshots/desktop_*.png. */
class ScreenshotTest {
    private val today = LocalDate.of(2026, 11, 4).toEpochDay()
    private val t0 = 1_780_000_000_000L

    private fun e(id: String, title: String, day: Long?, type: EntryType = EntryType.ONE_TIME) =
        EntryEntity(id = id, type = type.name, title = title, dueDay = day, originalDay = day, createdAt = t0 + id.hashCode(), updatedAt = t0)

    private val data = PinData(
        entries = listOf(
            e("1", "Отправить отчёт по серверам", today).copy(originalDay = today - 3, rollovers = 3, priority = Priority.HIGH.level, listId = "lw", tags = "работа",
                body = "- [x] собрать логи\n- [ ] свести таблицу\n\nСсылка: https://example.com/report"),
            e("2", "Записаться к стоматологу", today).copy(originalDay = today - 1, rollovers = 1),
            e("3", "Созвон с поставщиком", today).copy(timeMinutes = 11 * 60, listId = "lw", tags = "работа", attachments = "a1b2c3d4/Договор поставки.pdf"),
            e("4", "Зарядка", today, EntryType.RECURRING).copy(timeMinutes = 7 * 60 + 30, repeatRule = "daily", repeatAnchor = today - 10),
            e("5", "Купить продукты", today).copy(listId = "lh", body = "- [ ] молоко\n- [ ] хлеб\n- [x] яйца"),
            e("6", "Полить цветы", today).copy(status = TaskStatus.DONE.name, completedDay = today, completedAt = t0),
            e("7", "Поменять резину", today + 2).copy(listId = "lc"),
            e("8", "Оплатить интернет", today + 5).copy(priority = Priority.HIGH.level),
            e("p", "Покрасить забор", null, EntryType.PLAN).copy(planScope = "someday", body = "- [ ] купить краску\n- [ ] кисти"),
            e("q", "Отпуск на море", null, EntryType.PLAN).copy(planScope = "2027", tags = "отдых"),
            e("h", "Зарядка 15 минут", null, EntryType.HABIT).copy(repeatAnchor = today - 30, timeMinutes = 7 * 60),
            e("b", "Поздравить: Аня", today + 3, EntryType.BIRTHDAY).copy(personId = "p1", birthYear = 1990,
                repeatRule = LocalDate.ofEpochDay(today + 3).let { "yearly %02d-%02d".format(it.monthValue, it.dayOfMonth) }, repeatAnchor = today + 3),
        ),
        people = listOf(
            PersonEntity("p1", "Аня", "FRIEND", createdAt = t0, updatedAt = t0),
            PersonEntity("p2", "Мама", "FAMILY", note = "любит пионы", createdAt = t0, updatedAt = t0),
        ),
        lists = listOf(
            ListEntity("lw", "Работа", "BLUE", 0, t0, t0),
            ListEntity("lh", "Дом", "GREEN", 1, t0, t0),
            ListEntity("lc", "Машина", "ORANGE", 2, t0, t0),
        ),
        journal = (1..6).map { JournalEntity(entryId = "6", event = JournalEvent.DONE, day = today - it * 2L, at = t0, kind = "ONE_TIME", priority = 1, rollovers = 0) } +
            listOf(0L, 1, 2, 4, 5).map { JournalEntity(entryId = "h", event = JournalEvent.DONE, day = today - it, at = t0, kind = "HABIT", priority = 1, rollovers = 0) },
        blobs = emptyMap(),
    )

    private fun shot(name: String, dark: Boolean = false, width: Int = 1120, height: Int = 820, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dir = File("../app/build/screenshots").apply { mkdirs() }
        val scene = ImageComposeScene(width, height, Density(1f)) { PinTheme(dark) { content() } }
        try {
            scene.render() // первый кадр — раскладка
            val image = scene.render(1_000_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: return
            File(dir, "desktop_$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun screenshots() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        shot("today") { TodayPane(data, today) }
        shot("calendar") { CalendarPane(data, today) }
        shot("calendar_dark", dark = true) { CalendarPane(data, today) }
        shot("welcome") { Welcome({}, {}) }
        shot("people") { PeoplePane(data, today, null) }
        shot("plans") { PlansPane(data, today, null) }
        shot("habits") { HabitsPane(data, today, null) }
        shot("settings") { SettingsPane("G:\\Мой диск\\Pin\\Pin.zip", onChoose = {}) }
        shot("person", width = 560, height = 640) { PersonContent(data, "p1", today, { _, _, _, _, _ -> }, {}, {}) }
        shot("editor", width = 620, height = 1100) {
            EditorContent(data, EditTarget("1", today), today, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, pickFile = { null })
        }
        shot("editor_new", width = 620, height = 780) {
            EditorContent(data, EditTarget(null, today), today, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, pickFile = { null })
        }
    }
}
