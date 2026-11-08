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

    /** Когда начинается новый день (как в настройках телефона): до этого времени — ещё вчера. */
    var boundaryMinutes by mutableStateOf(prefs.getInt("boundary", LogicalDay.DEFAULT_BOUNDARY_MINUTES))
        private set

    fun setBoundary(minutes: Int) {
        boundaryMinutes = minutes
        prefs.putInt("boundary", minutes)
        runCatching { prefs.flush() }
    }

    /** Логический день по текущей границе. */
    fun logicalDay(): LogicalDay = LogicalDay(Clock.systemDefaultZone(), boundaryMinutes)
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
