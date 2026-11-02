package com.sonnik.pin.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonnik.pin.PinApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Настройки оформления — на устройстве (SharedPreferences), как в SonnikToPower; в синхронизацию не попадают. */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _palette = MutableStateFlow(ThemePalette.parse(prefs.getString(KEY_PALETTE, null)))
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()

    private val _pureBlack = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK, false))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    fun setMode(value: ThemeMode) {
        prefs.edit().putString(KEY_MODE, value.name).apply()
        _mode.value = value
    }

    fun setPalette(value: ThemePalette) {
        prefs.edit().putString(KEY_PALETTE, value.name).apply()
        _palette.value = value
    }

    fun setPureBlack(value: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK, value).apply()
        _pureBlack.value = value
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_PALETTE = "theme_palette"
        const val KEY_PURE_BLACK = "pure_black"
    }
}

/** Схема цветов палитры [palette] в светлом или тёмном варианте. */
@Composable
@ReadOnlyComposable
fun colorSchemeFor(palette: ThemePalette, dark: Boolean, pureBlack: Boolean = false): ColorScheme {
    if (palette == ThemePalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return if (dark && pureBlack) scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0C),
            surfaceContainer = Color(0xFF111214),
            surfaceContainerHigh = Color(0xFF1B1C1F),
            surfaceContainerHighest = Color(0xFF151618),
        ) else scheme
    }
    return staticColorScheme(palette, dark, pureBlack)
}

/** Цвета обоев есть только с Android 12. */
val ThemePalette.Companion.available: List<ThemePalette>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ThemePalette.entries else ThemePalette.entries - ThemePalette.DYNAMIC

/** Тёмная ли тема для выбранного режима. */
@Composable
@ReadOnlyComposable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Тема Pin по настройкам оформления (вне приложения — например, в превью — родная палитра). */
@Composable
fun PinTheme(content: @Composable () -> Unit) {
    val settings = (LocalContext.current.applicationContext as? PinApp)?.themeSettings
    if (settings == null) {
        MaterialTheme(colorScheme = colorSchemeFor(ThemePalette.PIN, isSystemInDarkTheme()), content = content)
        return
    }
    val mode by settings.mode.collectAsStateWithLifecycle()
    val palette by settings.palette.collectAsStateWithLifecycle()
    val pureBlack by settings.pureBlack.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = colorSchemeFor(palette, isDarkTheme(mode), pureBlack), content = content)
}
