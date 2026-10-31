package com.sonnik.pin.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonnik.pin.MainActivity
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.data.SettingsEntity
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.StatEvent
import com.sonnik.pin.engine.TaskStatus
import com.sonnik.pin.engine.TodayBoard
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Рисует экраны с примерными данными в app/build/screenshots — чтобы видеть настоящую отрисовку Android
 * без телефона. Ничего не проверяет. Запускается только при ручной сборке (SCREENSHOTS=1).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val today = LocalDate.of(2026, 11, 4).toEpochDay()

    @Before
    fun onlyOnDemand() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
    }

    private fun entry(
        id: String,
        title: String,
        day: Long = today,
        original: Long = day,
        time: Int? = null,
        priority: Int = 1,
        tags: String = "",
        rollovers: Int = 0,
        status: TaskStatus = TaskStatus.OPEN,
        repeat: RepeatRule? = null,
        completedDay: Long? = null,
        body: String = "",
    ) = EntryEntity(
        id = id,
        type = (if (repeat != null) EntryType.RECURRING else EntryType.ONE_TIME).name,
        title = title,
        body = body,
        dueDay = day,
        originalDay = original,
        timeMinutes = time,
        priority = priority,
        tags = tags,
        rollovers = rollovers,
        status = status.name,
        completedDay = completedDay,
        createdAt = id.hashCode().toLong(),
        updatedAt = 0,
        repeatRule = repeat?.let(RepeatRule::format),
        repeatAnchor = repeat?.let { today - 30 },
    )

    private val sample = listOf(
        entry("1", "Отправить отчёт по серверам", original = today - 3, rollovers = 3, priority = 2, tags = "работа"),
        entry("2", "Записаться к стоматологу", original = today - 1, rollovers = 1),
        entry("3", "Созвон с поставщиком", time = 11 * 60, tags = "работа"),
        entry("4", "Зарядка", repeat = RepeatRule.Daily, time = 7 * 60 + 30),
        entry("5", "Купить хлеб и молоко", tags = "дом", priority = 0),
        entry("6", "Полить цветы", repeat = RepeatRule.EveryNDays(3), status = TaskStatus.OPEN, day = today + 2),
        entry("7", "Оплатить интернет", status = TaskStatus.DONE, completedDay = today),
        entry("8", "Вынести мусор", repeat = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), day = today + 1),
        entry("9", "Забрать посылку", day = today + 1, time = 18 * 60),
        entry("10", "Поменять резину", day = today + 5, priority = 2, tags = "машина"),
    )

    private fun ui(list: List<EntryEntity>, settings: SettingsEntity = SettingsEntity()): PinUi {
        val byId = list.associateBy { it.id }
        return PinUi(
            today = today,
            board = TodayBoard.build(list.filter { it.dueDay != null }.mapNotNull { it.toSnapshot() }, today, doneDayOf = { byId[it.id]?.completedDay }),
            entries = byId,
            settings = settings,
        )
    }

    @Test
    fun screens() {
        val ui = ui(sample)
        shot("1_today") { TodayScreen(ui, {}, {}, { _, _ -> }, {}) }
        shot("2_today_empty") { TodayScreen(ui(emptyList()), {}, {}, { _, _ -> }, {}) }
        val done = listOf(
            JournalEntity(1, "7", "DONE", today, 0, "ONE_TIME", 1, 0, true),
            JournalEntity(2, "3", "DONE", today - 2, 0, "ONE_TIME", 1, 0, true),
            JournalEntity(3, "1", "DONE", today - 6, 0, "ONE_TIME", 1, 2, false),
        )
        shot("3_calendar") { CalendarScreen(ui, { _, _ -> flowOf(done) }, {}, {}, { _, _ -> }, {}) }
        shot("4_editor_new") { EditorScreen(null, today, onSave = {}, onDelete = {}, onToggleDone = {}, onClose = {}) }
        shot("5_editor_existing") {
            EditorScreen(
                sample[0].copy(body = "- [x] собрать логи\n- [ ] свести таблицу"),
                today,
                onSave = {},
                onDelete = {},
                onToggleDone = {},
                onClose = {},
            )
        }
        shot("6_editor_recurring") { EditorScreen(sample[7], today, onSave = {}, onDelete = {}, onToggleDone = {}, onClose = {}) }
        shot("7_settings") { SettingsScreen(SettingsEntity(), {}, {}, {}, {}, {}, {}) }

        val people = listOf(
            PersonEntity("p1", "Аня", Relation.WIFE.name, createdAt = 0, updatedAt = 0),
            PersonEntity("p2", "Серёга", Relation.FRIEND.name, createdAt = 0, updatedAt = 0),
            PersonEntity("p3", "Мама", Relation.FAMILY.name, createdAt = 0, updatedAt = 0),
        )
        val extra = listOf(
            entry("b1", "Поздравить: Аня", day = today + 6, repeat = null).copy(
                type = EntryType.BIRTHDAY.name, personId = "p1", birthYear = 1997, repeatRule = "yearly 11-10", repeatAnchor = today + 6,
            ),
            entry("b3", "Поздравить: Мама", day = today + 120, repeat = null).copy(
                type = EntryType.BIRTHDAY.name, personId = "p3", repeatRule = "yearly 03-04", repeatAnchor = today + 120,
            ),
            entry("h1", "День народного единства", repeat = null).copy(type = EntryType.HOLIDAY.name, repeatRule = "yearly 11-04"),
            entry("g1", "Наушники", day = 0, repeat = null).copy(
                type = EntryType.GIFT.name, dueDay = null, personId = "p1", occasionId = "b1", giftStatus = "IDEA", price = "12 000 ₽",
            ),
            entry("g2", "Книга по фотографии", repeat = null).copy(
                type = EntryType.GIFT.name, dueDay = null, personId = "p1", giftStatus = "BOUGHT", link = "https://example.com/book",
            ),
            entry("pl1", "Съездить в Казань", repeat = null).copy(type = EntryType.PLAN.name, dueDay = null, planScope = "2027"),
            entry("pl2", "Поменять масло", repeat = null).copy(type = EntryType.PLAN.name, dueDay = null, planScope = "2026-12", tags = "машина"),
            entry("pl3", "Выучить испанский", repeat = null).copy(type = EntryType.PLAN.name, dueDay = null, planScope = "someday"),
        )
        val all = sample + extra
        val full = ui(all).copy(
            people = people,
            events = PinRepository.occasionNotices(all, people, today).let { (h, n) -> h + n.map(Occasions::describe) },
        )
        shot("8_today_events") { TodayScreen(full, {}, {}, { _, _ -> }, {}) }
        shot("9_people") { PeopleScreen(full, {}, {}, {}) }
        shot("10_person") { PersonScreen(full, "p1", { _, _, _, _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}) }
        shot("11_plans") { PlansScreen(full, { _, _, _, _, _ -> }, { _, _, _, _, _, _ -> }, {}, {}) }
        val stats = (0 until 30).flatMap { i ->
            val d = today - i
            List((i * 7) % 4 + 1) { StatEvent("1", "DONE", d, EntryType.ONE_TIME, if (it == 0) 2 else 1, 0, it != 1) } +
                List(if (i % 5 == 2) 1 else 0) { StatEvent("2", "ROLLED", d, EntryType.ONE_TIME, 1, 1, false) } +
                List(if (i % 3 == 0) 1 else 0) { StatEvent("4", "DONE", d, EntryType.RECURRING, 1, 0, true) }
        }
        shot("13_stats") { StatsScreen(full, stats, {}, {}) }
        shot("12_holidays") { HolidaysScreen(full, { _, _, _, _ -> }, { _, _ -> }, {}, {}) }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent { PinTheme { content() } }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
