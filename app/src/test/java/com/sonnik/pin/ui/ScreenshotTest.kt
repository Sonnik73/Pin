package com.sonnik.pin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
        entry("5", "Купить хлеб и молоко", tags = "дом", priority = 0, body = "- [x] хлеб\n- [ ] молоко\n- [ ] [сыр по акции](https://example.com)"),
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
                sample[0].copy(body = "- [x] собрать логи\n- [ ] свести таблицу", attachments = "a1b2c3d4/Логи сервера.zip\ne5f6a7b8/Таблица.xlsx"),
                today,
                onAttach = {},
                onSave = {},
                onDelete = {},
                onToggleDone = {},
                onClose = {},
            )
        }
        shot("5b_editor_reminders") {
            EditorScreen(sample[2].copy(reminders = "0,120,1440"), today, onSave = {}, onDelete = {}, onToggleDone = {}, onClose = {})
        }
        shot("6_editor_recurring") { EditorScreen(sample[7], today, onSave = {}, onDelete = {}, onToggleDone = {}, onClose = {}) }
        shot("7_settings") { SettingsScreen(SettingsEntity(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }

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
            entry("pl1", "Съездить в Казань", repeat = null).copy(
                type = EntryType.PLAN.name, dueDay = null, planScope = "2027",
                body = "- [x] выбрать даты\n- [ ] билеты на поезд\n- [ ] [Кремль и Кул-Шариф](https://example.com/kazan)",
            ),
            entry("pl2", "Поменять масло", repeat = null).copy(type = EntryType.PLAN.name, dueDay = null, planScope = "2026-12", tags = "машина"),
            entry("pl3", "Выучить испанский", repeat = null).copy(type = EntryType.PLAN.name, dueDay = null, planScope = "someday"),
            entry("hb1", "Зарядка 15 минут", repeat = null).copy(type = EntryType.HABIT.name, dueDay = null, timeMinutes = 7 * 60 + 30),
            entry("hb2", "Читать 20 страниц", repeat = null).copy(type = EntryType.HABIT.name, dueDay = null),
        )
        val all = sample + extra
        val full = ui(all).copy(
            people = people,
            events = PinRepository.occasionNotices(all, people, today).let { (h, n) -> h + n.map(Occasions::describe) },
            habits = listOf(
                com.sonnik.pin.engine.HabitInfo("hb1", null, today - 120, (today - 120..today).filter { it % 7 != 3L }.toSet()),
                com.sonnik.pin.engine.HabitInfo("hb2", null, today - 60, (today - 60 until today).filter { it % 3 != 0L }.toSet()),
            ),
        )
        val lists = listOf(
            com.sonnik.pin.data.ListEntity("lw", "Работа", "BLUE", 0, 0, 0),
            com.sonnik.pin.data.ListEntity("lh", "Дом", "GREEN", 1, 0, 0),
            com.sonnik.pin.data.ListEntity("lc", "Машина", "ORANGE", 2, 0, 0),
        )
        val inLists = mapOf("1" to "lw", "3" to "lw", "5" to "lh", "6" to "lh", "8" to "lh", "10" to "lc")
        val listed = ui(all.map { e -> e.copy(listId = inLists[e.id], attachments = if (e.id == "3") "a1b2c3d4/Договор поставки.pdf" else null) }).copy(people = people, lists = lists, habits = full.habits)
        shot("26_lists") { ListsScreen(lists, mapOf("lw" to 2, "lh" to 3, "lc" to 1), { _, _, _ -> }, {}, {}) }
        shot("27_today_lists") { TodayScreen(listed, {}, {}, { _, _ -> }, {}, expandedIds = setOf("5")) }
        shot("28_editor_list") {
            EditorScreen(listed.entries.getValue("5"), today, lists = lists, onSave = {}, onDelete = {}, onToggleDone = {}, onClose = {})
        }
        val zone = java.time.ZoneId.systemDefault()
        fun at(day: Long, h: Int, m: Int = 0) = java.time.LocalDate.ofEpochDay(day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        fun utc(day: Long) = java.time.LocalDate.ofEpochDay(day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val phone = listOf(
            com.sonnik.pin.engine.PhoneEvent(1, "Планёрка", at(today, 10), at(today, 10, 30), false, 0xFF3F51B5.toInt(), "Работа"),
            com.sonnik.pin.engine.PhoneEvent(2, "Командировка в Самару", utc(today + 7), utc(today + 10), true, null, "Личный"),
            com.sonnik.pin.engine.PhoneEvent(3, "Стоматолог", at(today + 2, 17), at(today + 3, 18), false, 0xFF009688.toInt(), "Личный"),
        )
        shot("30_calendar_phone") {
            CalendarScreen(listed, { _, _ -> flowOf(emptyList()) }, {}, {}, { _, _ -> }, {}, phoneEvents = { _, _ -> phone })
        }
        shot("29_matrix") { MatrixScreen(listed, {}, { _, _ -> }, { _, _ -> }, {}) }
        shot("31_features") {
            FeaturesScreen(setOf(com.sonnik.pin.engine.Feature.MATRIX, com.sonnik.pin.engine.Feature.PLANS), { _, _ -> }, {})
        }
        shot("32_today_trimmed") {
            val off = setOf(
                com.sonnik.pin.engine.Feature.HABITS, com.sonnik.pin.engine.Feature.REVIEW,
                com.sonnik.pin.engine.Feature.MATRIX, com.sonnik.pin.engine.Feature.PLANS, com.sonnik.pin.engine.Feature.STATS,
            )
            androidx.compose.runtime.CompositionLocalProvider(LocalDisabledFeatures provides off) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { TodayScreen(listed, {}, {}, { _, _ -> }, {}) }
                    PinNavigationBar(Tab.TODAY) {}
                }
            }
        }
        val release = com.sonnik.pin.engine.ReleaseInfo(
            "v0.37", "https://github.com", "https://github.com/x.apk",
            "- Окна обновлений стали понятнее\n- **Важное** исправление", publishedDay = today - 1, apkBytes = 4_404_019,
        )
        shot("35_updates") { UpdatePreviews(today, release) }
        shot("36_update_offer") { UpdateOfferPreview(release) }
        shot("37_share_people") {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                SharePeoplePanel(full.shareRows(), {}, {}, initial = setOf("p1", "p3"))
            }
        }
        shot("38_import_people") {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                ImportPeoplePanel(
                    com.sonnik.pin.engine.PeopleImport.plan(
                        mapOf("Аня" to true, "Мама" to false),
                        listOf(
                            com.sonnik.pin.engine.SharedPerson("Аня", month = 11, day = 10),
                            com.sonnik.pin.engine.SharedPerson("Мама", month = 3, day = 4),
                            com.sonnik.pin.engine.SharedPerson("Тётя Света", "FAMILY", month = 7, day = 21, year = 1970),
                        ),
                    ),
                    {}, {},
                )
            }
        }
        shot("41_quick_edit") {
            androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = androidx.compose.ui.Modifier.padding(top = 300.dp),
            ) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 24.dp)) {
                    QuickEditContent(listed.entries.getValue("5"), today, {}, {})
                }
            }
        }
        shot("42_empty_art") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.foundation.layout.Row {
                    EmptyIllustration(EmptyArt.TODAY)
                    EmptyIllustration(EmptyArt.PLANS)
                }
                androidx.compose.foundation.layout.Row {
                    EmptyIllustration(EmptyArt.PEOPLE)
                    EmptyIllustration(EmptyArt.SEARCH)
                }
                EmptyIllustration(EmptyArt.LISTS)
                // Значок приложения.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.size(140.dp).background(androidx.compose.ui.graphics.Color(0xFFF4EEDC), androidx.compose.foundation.shape.RoundedCornerShape(32.dp)),
                ) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.sonnik.pin.R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = androidx.compose.ui.Modifier.size(140.dp),
                    )
                }
            }
        }
        shot("33_habits_tab") {
            androidx.compose.runtime.CompositionLocalProvider(LocalDisabledFeatures provides setOf(com.sonnik.pin.engine.Feature.STATS)) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { HabitsTabScreen(full, {}, {}, {}, {}) }
                    PinNavigationBar(Tab.HABITS) {}
                }
            }
        }
        big("today") {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { TodayScreen(full, {}, {}, { _, _ -> }, {}) }
                PinNavigationBar(Tab.CALENDAR) {}
            }
        }
        big("calendar") { CalendarScreen(full, { _, _ -> flowOf(emptyList()) }, {}, {}, { _, _ -> }, {}) }
        big("stats") { StatsScreen(full, emptyList(), {}, {}) }
        big("settings") { SettingsScreen(SettingsEntity(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        big("editor") { EditorScreen(listed.entries.getValue("5"), today, lists = lists, onSave = {}, onDelete = {}, onToggleDone = {}, onClose = {}) }
        big("matrix") { MatrixScreen(listed, {}, { _, _ -> }, { _, _ -> }, {}) }
        big("plans") { PlansScreen(full, { _, _, _, _, _ -> }, { _, _, _, _, _, _, _ -> }, {}, {}, expandedIds = setOf("pl1")) }
        big("people") { PeopleScreen(full, {}, {}, {}) }
        val moments = listOf(
            com.sonnik.pin.data.momentEntry(null, "m1", "Отпуск", "Море, Сочи", today - 2, com.sonnik.pin.engine.Moments.endOf(today - 2, 17), "отдых", 0),
            com.sonnik.pin.data.momentEntry(null, "m2", "Пришла награда", "Грамота за проект", today, null, "работа", 0),
            com.sonnik.pin.data.momentEntry(null, "m3", "Переехали в новую квартиру", "", today - 430, null, "", 0),
            com.sonnik.pin.data.momentEntry(null, "m4", "Свадьба", "", today - 2200, null, "", 0),
        )
        big("moments") {
            MomentsScreen(ui(moments), { _, _, _, _, _, _ -> }, {}, {}, switcher = { PlansSwitcher(PlansSection.MOMENTS) {} })
        }
        big("person") { PersonScreen(full, "p1", { _, _, _, _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}) }
        big("habit") { HabitScreen(full, "hb1", { _, _, _ -> }, {}, {}, {}) }
        big("all_entries") { AllEntriesScreen(full, {}, {}) }
        big("review") { ReviewScreen(full, { _, _ -> }, {}) }
        big("lists") { ListsScreen(lists, mapOf("lw" to 2, "lh" to 3, "lc" to 1), { _, _, _ -> }, {}, {}) }
        shot("3b_calendar_events") { CalendarScreen(full, { _, _ -> flowOf(emptyList()) }, {}, {}, { _, _ -> }, {}) }
        shot("25_focus") {
            FocusScreen(
                com.sonnik.pin.engine.Focus.start(System.currentTimeMillis() - 7 * 60_000L, "1", round = 1),
                "Отправить отчёт по серверам",
                50,
                {}, {}, {},
            )
        }
        shot("23_habit") { HabitScreen(full, "hb1", { _, _, _ -> }, {}, {}, {}) }
        shot("24_stats_habits") { StatsScreen(full, emptyList(), {}, {}) }
        shot("22_review") { ReviewScreen(full, { _, _ -> }, {}) }
        shot("21_all_entries") { AllEntriesScreen(full, {}, {}) }
        shot("8_today_events") { TodayScreen(full, {}, {}, { _, _ -> }, {}) }
        shot("9_people") { PeopleScreen(full, {}, {}, {}) }
        shot("10_person") { PersonScreen(full, "p1", { _, _, _, _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}) }
        shot("11_plans") { PlansScreen(full, { _, _, _, _, _ -> }, { _, _, _, _, _, _, _ -> }, {}, {}, expandedIds = setOf("pl1")) }
        val stats = (0 until 30).flatMap { i ->
            val d = today - i
            List((i * 7) % 4 + 1) { StatEvent("1", "DONE", d, EntryType.ONE_TIME, if (it == 0) 2 else 1, 0, it != 1) } +
                List(if (i % 5 == 2) 1 else 0) { StatEvent("2", "ROLLED", d, EntryType.ONE_TIME, 1, 1, false) } +
                List(if (i % 3 == 0) 1 else 0) { StatEvent("4", "DONE", d, EntryType.RECURRING, 1, 0, true) }
        }
        shot("13_stats") { StatsScreen(full, stats, {}, {}) }
        shot("14_quick_add") {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                QuickAddField(today, {}, androidx.compose.ui.Modifier.fillMaxWidth(), initialText = "завтра в 18 позвонить маме !")
            }
        }
        widgetShot()
        val themes = (compose.activity.application as com.sonnik.pin.PinApp).themeSettings
        themes.setPalette(ThemePalette.GARRISON)
        themes.setMode(ThemeMode.DARK)
        shot("16_theme_garrison_dark") { TodayScreen(full, {}, {}, { _, _ -> }, {}) }
        themes.setPalette(ThemePalette.STEEL)
        themes.setMode(ThemeMode.LIGHT)
        shot("17_theme_steel_light") { TodayScreen(full, {}, {}, { _, _ -> }, {}) }
        shot("18_settings_appearance") { SettingsScreen(SettingsEntity(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        themes.setPalette(ThemePalette.PIN)
        themes.setMode(ThemeMode.SYSTEM)
        shot("19_changelog") { ChangelogScreen {} }
        shot("12_holidays") { HolidaysScreen(full, { _, _, _, _ -> }, { _, _ -> }, {}, {}) }
    }

    /** Виджет: RemoteViews разворачиваются в обычные View и рисуются в картинку. */
    private fun widgetShot() {
        compose.runOnUiThread {
            val context = compose.activity
            val lines = listOf(
                com.sonnik.pin.widget.WidgetLine("1", "Отправить отчёт по серверам", "↷3", true),
                com.sonnik.pin.widget.WidgetLine("2", "Зарядка", "07:30", false),
                com.sonnik.pin.widget.WidgetLine("3", "Созвон с поставщиком", "11:00", false),
                com.sonnik.pin.widget.WidgetLine("4", "Купить хлеб и молоко", "", false),
            )
            val views = com.sonnik.pin.widget.TodayWidget.buildViews(context, today, 6, lines, 2, "🎉 День народного единства")
            val parent = android.widget.FrameLayout(context)
            val view = views.apply(context, parent)
            val width = (320 * context.resources.displayMetrics.density).toInt()
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(android.graphics.Color.rgb(90, 110, 140)) }.also { view.draw(it) }
            val dir = File("build/screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "15_widget.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // Виджет календаря 4×4.
            val marks = mapOf(
                today to com.sonnik.pin.engine.DayMarks(listOf(com.sonnik.pin.engine.MarkKind.OPEN, com.sonnik.pin.engine.MarkKind.DONE), true, false, true),
                today + 1 to com.sonnik.pin.engine.DayMarks(listOf(com.sonnik.pin.engine.MarkKind.OPEN, com.sonnik.pin.engine.MarkKind.PROJECTED, com.sonnik.pin.engine.MarkKind.PROJECTED), false, false, false),
                today + 6 to com.sonnik.pin.engine.DayMarks(listOf(com.sonnik.pin.engine.MarkKind.PROJECTED), false, true, false),
                today + 9 to com.sonnik.pin.engine.DayMarks(emptyList(), true, false, false),
                today - 2 to com.sonnik.pin.engine.DayMarks(listOf(com.sonnik.pin.engine.MarkKind.DONE, com.sonnik.pin.engine.MarkKind.DONE), false, false, false),
            )
            val cal = com.sonnik.pin.widget.CalendarWidget.buildViews(
                context, 1, java.time.LocalDate.ofEpochDay(today).withDayOfMonth(1), today, marks,
                com.sonnik.pin.widget.CalendarSizes.of(320, 330, 5, 3),
                listOf(
                    com.sonnik.pin.engine.CalendarEvent(today, false, "День народного единства"),
                    com.sonnik.pin.engine.CalendarEvent(today + 6, true, "Аня", 29),
                    com.sonnik.pin.engine.CalendarEvent(today + 10, true, "Серёга"),
                ),
            ).apply(context, parent)
            val h = (330 * context.resources.displayMetrics.density).toInt()
            cal.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
            )
            cal.layout(0, 0, cal.measuredWidth, cal.measuredHeight)
            val calBitmap = Bitmap.createBitmap(cal.measuredWidth, cal.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(calBitmap).apply { drawColor(android.graphics.Color.rgb(90, 110, 140)) }.also { cal.draw(it) }
            FileOutputStream(File(dir, "39_calendar_widget.png")).use { calBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // Маленький виджет календаря (как у жены): 🎂 не должны обрезаться.
            val smallW = (320 * context.resources.displayMetrics.density).toInt()
            val smallH = (210 * context.resources.displayMetrics.density).toInt()
            val small = com.sonnik.pin.widget.CalendarWidget.buildViews(
                context, 2, java.time.LocalDate.ofEpochDay(today).withDayOfMonth(1), today, marks,
                com.sonnik.pin.widget.CalendarSizes.of(320, 210, 5, 3),
                listOf(
                    com.sonnik.pin.engine.CalendarEvent(today, false, "День народного единства"),
                    com.sonnik.pin.engine.CalendarEvent(today + 6, true, "Аня", 29),
                    com.sonnik.pin.engine.CalendarEvent(today + 10, true, "Серёга"),
                ),
            ).apply(context, parent)
            small.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(smallW, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(smallH, android.view.View.MeasureSpec.EXACTLY),
            )
            small.layout(0, 0, small.measuredWidth, small.measuredHeight)
            val smallBitmap = Bitmap.createBitmap(small.measuredWidth, small.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(smallBitmap).apply { drawColor(android.graphics.Color.rgb(90, 110, 140)) }.also { small.draw(it) }
            FileOutputStream(File(dir, "40_calendar_widget_small.png")).use { smallBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Тот же экран с крупным системным шрифтом (как «Размер шрифта» побольше на телефоне). */
    private fun big(name: String, content: @Composable () -> Unit) = shot("big_$name") {
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1.3f),
        ) { content() }
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
