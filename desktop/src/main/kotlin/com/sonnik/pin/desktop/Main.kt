package com.sonnik.pin.desktop

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sonnik.pin.engine.LogicalDay
import java.awt.FileDialog
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences
import kotlinx.coroutines.delay

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

private val LightColors = lightColorScheme(
    primary = Color(0xFFC8423A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF5E6470),
    secondaryContainer = Color(0xFFE6E2D6),
    tertiary = Color(0xFFB7791F),
    tertiaryContainer = Color(0xFFFBE9C8),
    onTertiaryContainer = Color(0xFF4A3000),
    background = Color(0xFFFBF8F1),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEDE8DC),
    onSurfaceVariant = Color(0xFF5B5F66),
    surfaceContainer = Color(0xFFF3EEE3),
    surfaceContainerLow = Color(0xFFF7F3EA),
    surfaceContainerHigh = Color(0xFFEDE8DC),
    outlineVariant = Color(0xFFD8D2C4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A7F),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF8C1D18),
    tertiary = Color(0xFFF0B45A),
    background = Color(0xFF16181C),
    surface = Color(0xFF16181C),
    surfaceContainer = Color(0xFF1F2227),
    surfaceContainerLow = Color(0xFF1B1D21),
    surfaceContainerHigh = Color(0xFF272A30),
)

@Composable
internal fun PinTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

internal enum class Tab(val title: String) { TODAY("Сегодня"), CALENDAR("Календарь") }

fun main() = application {
    val icon = remember {
        Thread.currentThread().contextClassLoader.getResourceAsStream("pin.png")?.use { BitmapPainter(loadImageBitmap(it)) }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pin",
        icon = icon,
        state = rememberWindowState(width = 1120.dp, height = 820.dp),
    ) {
        PinTheme { App(window) }
    }
}

@Composable
private fun App(window: ComposeWindow) {
    val scope = rememberCoroutineScope()
    val store = remember { PinStore(scope) }
    var tab by remember { mutableStateOf(Tab.TODAY) }
    val zone = ZoneId.systemDefault()
    val logical = remember { LogicalDay(Clock.systemDefaultZone(), DesktopPrefs.boundaryMinutes) }
    var today by remember { mutableLongStateOf(logical.today()) }
    /** Окно правки: null — закрыто. */
    var editing by remember { mutableStateOf<EditTarget?>(null) }

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

    val actions = remember(store) {
        Actions(
            toggle = { e, done -> store.commit { Edits.setDone(it, e.id, done, logical.today(), System.currentTimeMillis()) } },
            open = { e -> editing = EditTarget(e.id, e.dueDay ?: logical.today()) },
            add = { day -> editing = EditTarget(null, day) },
            quickAdd = { draft -> store.commit { Edits.quickAdd(it, draft, System.currentTimeMillis()).first } },
        )
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Spacer(Modifier.padding(top = 12.dp))
            Text("📌", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.padding(top = 12.dp))
            NavigationRailItem(
                selected = tab == Tab.TODAY,
                onClick = { tab = Tab.TODAY },
                icon = { Icon(Icons.Default.Home, null) },
                label = { Text(Tab.TODAY.title) },
            )
            NavigationRailItem(
                selected = tab == Tab.CALENDAR,
                onClick = { tab = Tab.CALENDAR },
                icon = { Icon(Icons.Default.DateRange, null) },
                label = { Text(Tab.CALENDAR.title) },
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            FileBar(store.path, store.loaded, store.saving, zone, onChoose = ::choose, onReload = store::reload)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val data = store.loaded?.data
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalActions provides actions) {
                    when {
                        store.path == null -> Welcome(onChoose = ::choose)
                        data == null -> Centered(store.loaded?.error ?: "Читаю файл…")
                        tab == Tab.TODAY -> TodayPane(data, today)
                        else -> CalendarPane(data, today)
                    }
                }
            }
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

@Composable
private fun FileBar(path: String?, loaded: Loaded?, saving: Boolean, zone: ZoneId, onChoose: () -> Unit, onReload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                path ?: "Файл не выбран",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when {
                loaded?.error != null -> loaded.error
                saving -> "Сохраняю в файл…"
                loaded != null && loaded.modified > 0 ->
                    "Изменён " + DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm").format(Instant.ofEpochMilli(loaded.modified).atZone(zone)) +
                        " · обновляется сам, когда телефон синхронизируется · Pin $APP_VERSION"
                else -> "Pin $APP_VERSION для Windows"
            }
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = if (loaded?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (path != null) {
            IconButton(onClick = onReload) { Icon(Icons.Default.Refresh, "Перечитать") }
        }
        OutlinedButton(onClick = onChoose) { Text(if (path == null) "Открыть Pin.zip…" else "Другой файл…") }
    }
}

@Composable
private fun Welcome(onChoose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Pin на компьютере", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Программа показывает те же задачи, что на телефоне. Данные берутся из файла Pin.zip, " +
                    "который телефон хранит на Google Диске.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Step("1", "На телефоне: Pin → Настройки → «Google Диск — файлом» → «Создать файл на Диске».")
            Step("2", "На компьютере установите «Google Диск для компьютера» — диск появится в проводнике (обычно G:).")
            Step("3", "Нажмите кнопку ниже и выберите Pin.zip в папке Диска. Программа запомнит файл.")
            Spacer(Modifier.padding(top = 4.dp))
            Button(onClick = onChoose) { Text("Открыть Pin.zip…") }
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
