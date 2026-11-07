package com.sonnik.pin.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Feature
import com.sonnik.pin.engine.SearchState
import com.sonnik.pin.engine.LogicalDay
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.ui.PinIcons
import com.sonnik.pin.ui.ThemeMode
import com.sonnik.pin.ui.ThemePalette
import com.sonnik.pin.ui.staticColorScheme
import java.awt.FileDialog
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Настройки программы на этом компьютере (реестр Windows): путь к Pin.zip. */
object DesktopPrefs {
    private val prefs = Preferences.userRoot().node("com/sonnik/pin")

    var path: String?
        get() = prefs.get("zip", null)
        set(value) {
            if (value == null) prefs.remove("zip") else prefs.put("zip", value)
            prefs.flush()
        }

    var boundaryMinutes: Int
        get() = prefs.getInt("boundary", LogicalDay.DEFAULT_BOUNDARY_MINUTES)
        set(value) = prefs.putInt("boundary", value)
}

/** Оформление на этом компьютере — как «Оформление» в настройках телефона. */
object Appearance {
    private val prefs = Preferences.userRoot().node("com/sonnik/pin")

    var mode by mutableStateOf(ThemeMode.entries.firstOrNull { it.name == prefs.get("theme_mode", null) } ?: ThemeMode.SYSTEM)
        private set
    var palette by mutableStateOf(ThemePalette.parse(prefs.get("theme_palette", null)).let { if (it == ThemePalette.DYNAMIC) ThemePalette.PIN else it })
        private set
    var pureBlack by mutableStateOf(prefs.getBoolean("pure_black", false))
        private set

    fun update(mode: ThemeMode = this.mode, palette: ThemePalette = this.palette, pureBlack: Boolean = this.pureBlack) {
        this.mode = mode
        this.palette = palette
        this.pureBlack = pureBlack
        prefs.put("theme_mode", mode.name)
        prefs.put("theme_palette", palette.name)
        prefs.putBoolean("pure_black", pureBlack)
        prefs.flush()
    }
}

/** Выключенные функции на этом компьютере — как Настройки → «Функции» на телефоне. */
object DesktopFeatures {
    private val prefs = Preferences.userRoot().node("com/sonnik/pin")

    var disabled by mutableStateOf(Feature.parseDisabled(prefs.get("disabled_features", null)))
        private set

    fun set(feature: Feature, on: Boolean) {
        disabled = if (on) disabled - feature else disabled + feature
        prefs.put("disabled_features", Feature.formatDisabled(disabled))
        prefs.flush()
    }
}

/** Включена ли часть программы. */
fun Feature.on(): Boolean = this !in DesktopFeatures.disabled

@Composable
internal fun isDark(): Boolean = when (Appearance.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
internal fun PinTheme(dark: Boolean = isDark(), palette: ThemePalette = Appearance.palette, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = staticColorScheme(palette, dark, Appearance.pureBlack)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

internal enum class Tab(val title: String, val feature: Feature? = null) {
    TODAY("Сегодня"),
    CALENDAR("Календарь"),
    PEOPLE("Люди", Feature.PEOPLE),
    PLANS("Планы", Feature.PLANS),
    HABITS("Привычки", Feature.HABITS),
    STATS("Итоги", Feature.STATS),
    MATRIX("Матрица", Feature.MATRIX),
    FOCUS("Фокус", Feature.FOCUS),
    SEARCH("Поиск"),
    SETTINGS("Настройки"),
}

fun main(args: Array<String>) = application {
    val icon = remember {
        Thread.currentThread().contextClassLoader.getResourceAsStream("pin.png")?.use { BitmapPainter(loadImageBitmap(it)) }
    }
    val trayState = rememberTrayState()
    // «--tray» — запуск вместе с Windows: сразу в трей, без окна.
    var visible by remember { mutableStateOf("--tray" !in args) }
    Notifier.tray = trayState
    if (icon != null) {
        Tray(
            icon = icon,
            state = trayState,
            tooltip = "Pin",
            onAction = { visible = true },
            menu = {
                Item("Открыть Pin", onClick = { visible = true })
                Item("Выход", onClick = ::exitApplication)
            },
        )
    }
    Window(
        onCloseRequest = {
            if (TrayMode.onClose && icon != null) {
                // Закрытие окна — в трей: напоминания и фокус-таймер продолжают работать.
                visible = false
                if (!TrayMode.hinted) {
                    TrayMode.hinted = true
                    Notifier.show("Pin работает в трее", "Напоминания будут приходить. Выйти — правой кнопкой по значку Pin.")
                }
            } else {
                exitApplication()
            }
        },
        title = "Pin",
        icon = icon,
        visible = visible,
        state = rememberWindowState(width = 1180.dp, height = 860.dp),
    ) {
        PinTheme { App(window) }
    }
}

/** Уведомления Windows — через значок Pin в области уведомлений. */
object Notifier {
    var tray: TrayState? = null

    fun show(title: String, text: String) {
        val t = tray
        if (t != null) t.sendNotification(Notification(title, text, Notification.Type.Info)) else java.awt.Toolkit.getDefaultToolkit().beep()
    }
}

@Composable
private fun App(window: ComposeWindow) {
    val scope = rememberCoroutineScope()
    val store = remember { PinStore(scope) }
    var tab by remember { mutableStateOf(Tab.TODAY) }
    val zone = ZoneId.systemDefault()
    val logical = remember { LogicalDay(Clock.systemDefaultZone(), DesktopPrefs.boundaryMinutes) }
    val focusTimer = remember {
        FocusTimer { taskId, minutes, startedAt ->
            store.commit { Edits.logFocus(it, taskId, minutes, startedAt, FocusTimer.dayOf(startedAt)) }
        }
    }
    // Напоминания о задачах, привычках и утренняя сводка — уведомлениями Windows.
    LaunchedEffect(Unit) {
        while (true) {
            val data = store.loaded?.data
            if (data != null) {
                val now = System.currentTimeMillis()
                DesktopReminders.check(data, logical.today(), now, zone, DesktopPrefs.boundaryMinutes)
                    .take(6).forEach { (title, text) -> Notifier.show(title, text) }
            }
            delay(30_000)
        }
    }
    // Новая версия на GitHub — не чаще раза в 6 часов, если не выключено.
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { UpdateCheck.check() }?.let { Notifier.show("Вышла Pin ${it.version}", "Скачать можно в программе: плашка вверху окна.") }
            delay(60 * 60_000L)
        }
    }
    // Конец фазы фокуса — уведомление Windows, даже если окно свёрнуто.
    LaunchedEffect(Unit) {
        while (true) {
            focusTimer.tick()?.let { Notifier.show("Pin — фокус", it) }
            delay(1_000)
        }
    }
    var today by remember { mutableLongStateOf(logical.today()) }
    /** Окно правки: null — закрыто. */
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    var searchState by remember { mutableStateOf(SearchState.ALL) }
    var searchKey by remember { mutableIntStateOf(0) }

    // Новый логический день — без перезапуска программы.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            today = logical.today()
        }
    }
    // Файл на Google Диске меняется, когда телефон синхронизируется: перечитываем при каждом изменении.
    LaunchedEffect(Unit) { store.watch() }

    fun choose() {
        val dialog = FileDialog(window, "Выберите Pin.zip", FileDialog.LOAD).apply {
            file = "*.zip"
            setFilenameFilter { _, name -> name.endsWith(".zip", ignoreCase = true) }
            store.path?.let(::File)?.parentFile?.let { directory = it.path }
            isVisible = true
        }
        val name = dialog.file ?: return
        store.choose(File(dialog.directory, name).path)
    }

    fun createNew() {
        // Лучше сразу в папку Google Диска: тогда этот же файл потом подключит телефон.
        val home = File(System.getProperty("user.home"))
        val suggested = listOf(File("G:\\Мой диск"), File("G:\\My Drive"), File(home, "Documents"), home).firstOrNull { it.isDirectory }
        val dialog = FileDialog(window, "Где хранить задачи — новый Pin.zip", FileDialog.SAVE).apply {
            file = "Pin.zip"
            suggested?.let { directory = it.path }
            isVisible = true
        }
        var name = dialog.file ?: return
        if (!name.endsWith(".zip", ignoreCase = true)) name += ".zip"
        store.create(File(dialog.directory, name).path)
    }

    val actions = remember(store) {
        Actions(
            toggle = { e, done -> store.commit { Edits.setDone(it, e.id, done, logical.today(), System.currentTimeMillis()) } },
            open = { e ->
                // Задачи — в окне правки; остальное — на своей вкладке, как на телефоне.
                when (e.entryType) {
                    EntryType.ONE_TIME, EntryType.RECURRING -> editing = EditTarget(e.id, e.dueDay ?: logical.today())
                    EntryType.PLAN -> tab = Tab.PLANS
                    EntryType.HABIT -> tab = Tab.HABITS
                    EntryType.BIRTHDAY, EntryType.GIFT -> tab = Tab.PEOPLE
                    EntryType.HOLIDAY -> tab = Tab.CALENDAR
                }
            },
            add = { day -> editing = EditTarget(null, day) },
            quickAdd = { draft -> store.commit { Edits.quickAdd(it, draft, System.currentTimeMillis()).first } },
            reschedule = { e, day -> store.commit { Edits.reschedule(it, e.id, day, System.currentTimeMillis()) } },
            toPlans = { e -> store.commit { Edits.moveToPlans(it, e.id, System.currentTimeMillis()) } },
            skip = { e -> store.commit { Edits.skipOccurrence(it, e.id, logical.today(), System.currentTimeMillis()) } },
            delete = { e -> store.commit { Edits.delete(it, e.id, System.currentTimeMillis()) } },
            setBody = { e, body -> store.commit { Edits.setBody(it, e.id, body, System.currentTimeMillis()) } },
            toggleHabit = { id, day -> store.commit { Edits.toggleHabit(it, id, day, logical.today(), System.currentTimeMillis()) } },
            review = { reviewing = true },
            search = { state -> searchState = state; searchKey++; tab = Tab.SEARCH },
            focus = { e -> focusTimer.start(e.id); tab = Tab.FOCUS },
        )
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Spacer(Modifier.padding(top = 12.dp))
            Text("📌", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.padding(top = 12.dp))
            Tab.entries.filter { it.feature?.on() != false }.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                NavigationRailItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = {
                        Icon(
                            when (t) {
                                Tab.TODAY -> Icons.Default.Home
                                Tab.CALENDAR -> Icons.Default.DateRange
                                Tab.PLANS -> Icons.AutoMirrored.Filled.List
                                Tab.HABITS -> Icons.Default.CheckCircle
                                Tab.PEOPLE -> Icons.Default.Person
                                Tab.STATS -> PinIcons.Chart
                                Tab.MATRIX -> PinIcons.Matrix
                                Tab.FOCUS -> Icons.Default.PlayArrow
                                Tab.SEARCH -> Icons.Default.Search
                                Tab.SETTINGS -> Icons.Default.Settings
                            },
                            null,
                        )
                    },
                    label = { Text(t.title) },
                )
            }
            // Правки ещё не легли в файл (Google Диск держит его) — тихая отметка вместо плашки.
            if (store.offline) {
                Text("● нет связи\nс файлом", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
                Spacer(Modifier.padding(top = 4.dp))
            }
            if (store.unsaved) {
                Text("● не\nсохранено", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            store.problem?.let { ProblemBanner(it, onRetry = store::reload, onDismiss = store::dismissProblem) }
            UpdateCheck.available?.let { UpdateBanner(it.version, it.apkBytes) }
            val data = store.loaded?.data
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalActions provides actions) {
                    when {
                        tab == Tab.SETTINGS -> SettingsPane(
                            store.path, onChoose = ::choose, onCreate = ::createNew, modified = store.loaded?.modified,
                            data = data, store = store, today = today, offlineSince = store.offlineSince,
                        )
                        store.path == null -> Welcome(onChoose = ::choose, onCreate = ::createNew)
                        data == null -> Centered(store.problem ?: "Читаю файл…")
                        tab == Tab.TODAY -> TodayPane(data, today)
                        tab == Tab.CALENDAR -> CalendarPane(data, today)
                        tab == Tab.PLANS -> PlansPane(data, today, store)
                        tab == Tab.SEARCH -> SearchPane(data, today, searchState, searchKey)
                        tab == Tab.STATS -> StatsPane(data, today, onFocus = { tab = Tab.FOCUS })
                        tab == Tab.MATRIX -> MatrixPane(data, today) { id, important ->
                            store.commit { Edits.setPriority(it, id, if (important) Priority.HIGH else Priority.NORMAL, System.currentTimeMillis()) }
                        }
                        tab == Tab.FOCUS -> FocusPane(data, today, focusTimer)
                        tab == Tab.HABITS -> HabitsPane(data, today, store)
                        else -> PeoplePane(data, today, store)
                    }
                }
            }
        }
    }

    if (reviewing && store.loaded?.data != null) {
        CompositionLocalProvider(LocalActions provides actions) {
            ReviewWindow(store.loaded!!.data!!, today, onClose = { reviewing = false })
        }
    }

    val target = editing
    val data = store.loaded?.data
    if (target != null && data != null) {
        EditorWindow(
            data = data,
            target = target,
            today = today,
            onClose = { editing = null },
            onSave = { draft ->
                val now = System.currentTimeMillis()
                store.commit {
                    if (target.id == null) Edits.create(it, draft, now).first else Edits.update(it, target.id, draft, logical.today(), now)
                }
                editing = null
            },
            onDelete = { id -> store.commit { Edits.delete(it, id, System.currentTimeMillis()) }; editing = null },
            onToggle = { e, done -> actions.toggle(e, done) },
            onAttach = { id, name, bytes -> store.commit { Edits.addAttachment(it, id, name, bytes, System.currentTimeMillis()) } },
            onRemoveAttachment = { id, aid -> store.commit { Edits.removeAttachment(it, id, aid, System.currentTimeMillis()) } },
            onCreateList = { name -> store.commit { Edits.listIdFor(it, name, System.currentTimeMillis()).first } },
        )
    }
}

/** Плашка «Вышла новая версия»: скачать установщик .msi или пропустить версию. */
@Composable
private fun UpdateBanner(version: String, bytes: Long?) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Вышла Pin ${version.removePrefix("v")}" + (bytes?.let { " · " + com.sonnik.pin.engine.Updates.size(it) } ?: "") +
                    ". Скачайте установщик и запустите — он заменит эту версию, задачи останутся.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { openUrl(UpdateCheck.msiUrl ?: UpdateCheck.available?.pageUrl) }) { Text("Скачать") }
            TextButton(onClick = { openUrl(UpdateCheck.available?.pageUrl) }) { Text("Что нового") }
            TextButton(onClick = { UpdateCheck.skip() }) { Text("Пропустить") }
        }
    }
}

internal fun openUrl(url: String?) {
    if (url == null) return
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/** Плашка о беде с файлом: появляется, только если беда не проходит сама; закрывается крестиком. */
@Composable
private fun ProblemBanner(text: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Повторить") }
            IconButton(onClick = onDismiss) { Text("✕", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
internal fun Welcome(onChoose: () -> Unit, onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 600.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Pin на компьютере", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Задачи хранятся в одном файле Pin.zip. Его можно завести прямо здесь или взять тот, что уже ведёт телефон.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Начать с чистого листа", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Программа создаст пустой Pin.zip — телефон не нужен. Если положить файл в папку Google Диска " +
                            "(«Google Диск для компьютера», обычно диск G:), позже его можно подключить на телефоне: " +
                            "Настройки → «Google Диск — файлом» → «Подключить существующий файл» — задачи сольются.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onCreate) { Text("Создать новый Pin.zip…") }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Уже есть задачи на телефоне", style = MaterialTheme.typography.titleMedium)
                    Step("1", "На телефоне: Pin → Настройки → «Google Диск — файлом» → «Создать файл на Диске».")
                    Step("2", "На компьютере установите «Google Диск для компьютера» — диск появится в проводнике.")
                    Step("3", "Выберите этот Pin.zip в папке Диска. Программа запомнит файл.")
                    OutlinedButton(onClick = onChoose) { Text("Открыть Pin.zip…") }
                }
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(n, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
