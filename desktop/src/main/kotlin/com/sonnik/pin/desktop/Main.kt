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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private enum class Tab(val title: String) { TODAY("Сегодня"), CALENDAR("Календарь") }

/** Прочитанный файл: содержимое, время изменения файла и ошибка чтения. */
private data class Loaded(val data: PinData?, val modified: Long, val error: String?)

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
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                App(window)
            }
        }
    }
}

@Composable
private fun App(window: ComposeWindow) {
    var path by remember { mutableStateOf(DesktopPrefs.path) }
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var reloadTick by remember { mutableLongStateOf(0L) }
    val zone = ZoneId.systemDefault()
    val logical = remember { LogicalDay(Clock.systemDefaultZone(), DesktopPrefs.boundaryMinutes) }
    var today by remember { mutableLongStateOf(logical.today()) }
    val scope = rememberCoroutineScope()

    // Новый логический день — без перезапуска программы.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            today = logical.today()
        }
    }
    // Файл на Google Диске меняется, когда телефон синхронизируется: перечитываем при каждом изменении.
    LaunchedEffect(path, reloadTick) {
        val file = path?.let(::File) ?: run { loaded = null; return@LaunchedEffect }
        var lastModified = -1L
        while (true) {
            val modified = withContext(Dispatchers.IO) { if (file.isFile) file.lastModified() else 0L }
            if (modified != lastModified) {
                lastModified = modified
                loaded = withContext(Dispatchers.IO) {
                    when {
                        !file.isFile -> Loaded(loaded?.data, 0, "Файл не найден: ${file.path}")
                        else -> runCatching { Loaded(PinData.read(file.readBytes(), zone), modified, null) }
                            .getOrElse { Loaded(loaded?.data, modified, "Не удалось прочитать файл: ${it.message ?: it.javaClass.simpleName}") }
                    }
                }
            }
            delay(3_000)
        }
    }

    fun choose() {
        val dialog = FileDialog(window, "Выберите Pin.zip", FileDialog.LOAD).apply {
            file = "*.zip"
            setFilenameFilter { _, name -> name.endsWith(".zip", ignoreCase = true) }
            path?.let(::File)?.parentFile?.let { directory = it.path }
            isVisible = true
        }
        val name = dialog.file ?: return
        val chosen = File(dialog.directory, name).path
        DesktopPrefs.path = chosen
        path = chosen
        scope.launch { reloadTick++ }
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
            FileBar(path, loaded, zone, onChoose = ::choose, onReload = { reloadTick++ })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val data = loaded?.data
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    path == null -> Welcome(onChoose = ::choose)
                    data == null -> Centered(loaded?.error ?: "Читаю файл…")
                    tab == Tab.TODAY -> TodayPane(data, today)
                    else -> CalendarPane(data, today)
                }
            }
        }
    }
}

@Composable
private fun FileBar(path: String?, loaded: Loaded?, zone: ZoneId, onChoose: () -> Unit, onReload: () -> Unit) {
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
