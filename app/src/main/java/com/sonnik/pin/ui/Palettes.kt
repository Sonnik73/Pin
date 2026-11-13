package com.sonnik.pin.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Палитры без Android: этот файл собирается и в программу для Windows (модуль desktop).

/** Цвета булавки: красная головка на бумаге. */
object PinColors {
    val Red = Color(0xFFC8423A)
    val RedLight = Color(0xFFFF8A7F)
    val Amber = Color(0xFFB7791F)
    val AmberLight = Color(0xFFF0B45A)
    val Paper = Color(0xFFFBF8F1)
    val Ink = Color(0xFF1F2328)
    val Night = Color(0xFF16181C)

    /** Успех — выполненная задача: зелёный в любой палитре (оттенки «Стали»), красный оставлен важному. */
    val Done = Color(0xFF2E7D5B)
    val DoneDark = Color(0xFF1E4D37)
    val DoneMark = Color(0xFF8FD6AE)
}

/** Заливка и галочка отметки «выполнено»: [dark] — тёмная ли тема. */
fun doneColors(dark: Boolean): Pair<Color, Color> =
    if (dark) PinColors.DoneDark to PinColors.DoneMark else PinColors.Done to Color.White

/** Светлая, тёмная или как в системе. */
enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

/**
 * Палитры — как в SonnikToPower: основной акцент, второй акцент (у Pin — переносы и графики) и лёгкий оттенок фона;
 * [tintStrength] — насколько сильно оттенок окрашивает фон и карточки. [PIN] — родная палитра Pin (красная булавка
 * на бумаге), [DYNAMIC] — цвета обоев (Android 12+). Имена enum хранятся в настройках — не переименовывать.
 */
enum class ThemePalette(
    val title: String,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightTertiary: Color,
    val darkTertiary: Color,
    val tint: Color,
    val tintStrength: Float = 1f,
) {
    PIN("Булавка", PinColors.Red, PinColors.RedLight, PinColors.Amber, PinColors.AmberLight, Color(0xFFB59A5A)),
    STEEL("Сталь", Color(0xFF2F5C8F), Color(0xFFA9C7F0), Color(0xFF2E7D5B), Color(0xFF8FD6AE), Color(0xFF2F5C8F)),
    FOREST("Лес", Color(0xFF2E6B3F), Color(0xFF94D3A2), Color(0xFF7A5A14), Color(0xFFE0C27A), Color(0xFF3F6B4A)),
    SUNSET("Закат", Color(0xFFB4501C), Color(0xFFFFB68F), Color(0xFF00796B), Color(0xFF7FD3C6), Color(0xFFB4501C)),
    AMETHYST("Аметист", Color(0xFF6A4BA8), Color(0xFFCDB8FF), Color(0xFF2E7D7A), Color(0xFF86D5CF), Color(0xFF6A4BA8)),
    GRAPHITE("Графит", Color(0xFF3C4043), Color(0xFFD0D3D6), Color(0xFF5B6B78), Color(0xFFB4C2CC), Color(0xFF6B6F73)),

    /** «Кузница»: тёмный металл и раскалённое железо. */
    FORGE("Кузница", Color(0xFFB8441A), Color(0xFFFF8A50), Color(0xFF8D6E2A), Color(0xFFE3B95C), Color(0xFF7A4A30), tintStrength = 2.5f),

    /** «Гарнизон»: олива, бронза и пергамент — в тон «Крепости». */
    GARRISON("Гарнизон", Color(0xFF56682F), Color(0xFFBFD08A), Color(0xFF8C5A2B), Color(0xFFE2A66B), Color(0xFFA68A4E), tintStrength = 3f),

    DYNAMIC("Обои", Color(0xFF2F5C8F), Color(0xFFA9C7F0), Color(0xFF2E7D5B), Color(0xFF8FD6AE), Color(0xFF2F5C8F));

    companion object {
        fun parse(name: String?): ThemePalette = entries.firstOrNull { it.name == name } ?: PIN
    }
}

private val Light = lightColorScheme(
    primary = PinColors.Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF5E6470),
    secondaryContainer = Color(0xFFE6E2D6),
    onSecondaryContainer = PinColors.Ink,
    tertiary = PinColors.Amber,
    tertiaryContainer = Color(0xFFFBE9C8),
    onTertiaryContainer = Color(0xFF4A3000),
    background = PinColors.Paper,
    onBackground = PinColors.Ink,
    surface = PinColors.Paper,
    onSurface = PinColors.Ink,
    surfaceVariant = Color(0xFFEDE8DC),
    onSurfaceVariant = Color(0xFF5B5F66),
    surfaceContainer = Color(0xFFF3EEE3),
    surfaceContainerLow = Color(0xFFF7F3EA),
    surfaceContainerHigh = Color(0xFFEDE8DC),
    outlineVariant = Color(0xFFD8D2C4),
)

private val Dark = darkColorScheme(
    primary = PinColors.RedLight,
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF8C1D18),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFC3C7D0),
    secondaryContainer = Color(0xFF33363C),
    tertiary = PinColors.AmberLight,
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFF7DDB0),
    background = PinColors.Night,
    onBackground = Color(0xFFE6E2DA),
    surface = PinColors.Night,
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFB9BCC4),
    surfaceContainer = Color(0xFF1F2227),
    surfaceContainerLow = Color(0xFF1B1D22),
    surfaceContainerHigh = Color(0xFF282B31),
    outlineVariant = Color(0xFF3A3D44),
)

/*
 * Остальные палитры — от нейтральной основы SonnikToPower заменой акцентов и лёгким оттенком фона.
 */
private val BaseLight = lightColorScheme(
    primary = Color(0xFF2F5C8F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7F5),
    onPrimaryContainer = Color(0xFF0F2A47),
    secondary = Color(0xFF56606D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBF1),
    onSecondaryContainer = Color(0xFF1B2430),
    tertiary = Color(0xFF2E7D5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6EFE2),
    onTertiaryContainer = Color(0xFF0C3A26),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBE4E2),
    onErrorContainer = Color(0xFF5C1A16),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFE6E9EE),
    onSurfaceVariant = Color(0xFF5F6672),
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFFDDE1E6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val BaseDark = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    onPrimary = Color(0xFF0F2A47),
    primaryContainer = Color(0xFF2A4566),
    onPrimaryContainer = Color(0xFFDCE7F5),
    secondary = Color(0xFFBCC5D1),
    onSecondary = Color(0xFF26303B),
    secondaryContainer = Color(0xFF2C333C),
    onSecondaryContainer = Color(0xFFDCE3EC),
    tertiary = Color(0xFF8FD6AE),
    onTertiary = Color(0xFF0C3A26),
    tertiaryContainer = Color(0xFF1E4D37),
    onTertiaryContainer = Color(0xFFD6EFE2),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF4A1C19),
    onErrorContainer = Color(0xFFFBE4E2),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE3E5E9),
    surface = Color(0xFF111317),
    onSurface = Color(0xFFE3E5E9),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFA9B0BA),
    outline = Color(0xFF6E7681),
    outlineVariant = Color(0xFF30353D),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF16191D),
    surfaceContainer = Color(0xFF181B20),
    surfaceContainerHigh = Color(0xFF23272E),
    surfaceContainerHighest = Color(0xFF1C2026),
)

private val DarkBase = Color(0xFF111317)

private fun lightScheme(p: ThemePalette): ColorScheme = BaseLight.copy(
    primary = p.lightPrimary,
    primaryContainer = lerp(p.lightPrimary, Color.White, 0.86f),
    onPrimaryContainer = lerp(p.lightPrimary, Color.Black, 0.55f),
    secondary = lerp(BaseLight.secondary, p.tint, 0.2f * p.tintStrength),
    secondaryContainer = lerp(BaseLight.secondaryContainer, p.tint, 0.1f * p.tintStrength),
    tertiary = p.lightTertiary,
    tertiaryContainer = lerp(p.lightTertiary, Color.White, 0.85f),
    onTertiaryContainer = lerp(p.lightTertiary, Color.Black, 0.6f),
    background = lerp(Color(0xFFF4F5F7), p.tint, 0.05f * p.tintStrength),
    surface = lerp(Color(0xFFF4F5F7), p.tint, 0.05f * p.tintStrength),
    surfaceVariant = lerp(Color(0xFFE6E8EC), p.tint, 0.08f * p.tintStrength),
    outlineVariant = lerp(Color(0xFFDDE0E4), p.tint, 0.06f * p.tintStrength),
)

private fun darkScheme(p: ThemePalette): ColorScheme = BaseDark.copy(
    primary = p.darkPrimary,
    onPrimary = lerp(p.darkPrimary, Color.Black, 0.75f),
    primaryContainer = lerp(p.darkPrimary, DarkBase, 0.68f),
    onPrimaryContainer = lerp(p.darkPrimary, Color.White, 0.7f),
    secondary = lerp(BaseDark.secondary, p.tint, 0.2f * p.tintStrength),
    secondaryContainer = lerp(BaseDark.secondaryContainer, p.tint, 0.1f * p.tintStrength),
    tertiary = p.darkTertiary,
    onTertiary = lerp(p.darkTertiary, Color.Black, 0.75f),
    tertiaryContainer = lerp(p.darkTertiary, DarkBase, 0.7f),
    onTertiaryContainer = lerp(p.darkTertiary, Color.White, 0.7f),
    background = lerp(DarkBase, p.tint, 0.04f * p.tintStrength),
    surface = lerp(DarkBase, p.tint, 0.04f * p.tintStrength),
    surfaceVariant = lerp(BaseDark.surfaceVariant, p.tint, 0.08f * p.tintStrength),
    outlineVariant = lerp(BaseDark.outlineVariant, p.tint, 0.06f * p.tintStrength),
    surfaceContainerLowest = lerp(BaseDark.surfaceContainerLowest, p.tint, 0.03f * p.tintStrength),
    surfaceContainerLow = lerp(BaseDark.surfaceContainerLow, p.tint, 0.05f * p.tintStrength),
    surfaceContainer = lerp(BaseDark.surfaceContainer, p.tint, 0.05f * p.tintStrength),
    surfaceContainerHigh = lerp(BaseDark.surfaceContainerHigh, p.tint, 0.05f * p.tintStrength),
    surfaceContainerHighest = lerp(BaseDark.surfaceContainerHighest, p.tint, 0.05f * p.tintStrength),
)

/** Чёрный фон для AMOLED: экран не светится, карточки чуть светлее. */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF111214),
    surfaceContainerHigh = Color(0xFF1B1C1F),
    surfaceContainerHighest = Color(0xFF151618),
)

/** Схема палитры без цветов обоев: у «Обоев» (и вне Android) — основа «Стали». */
fun staticColorScheme(palette: ThemePalette, dark: Boolean, pureBlack: Boolean = false): ColorScheme {
    val scheme = when (palette) {
        ThemePalette.PIN -> if (dark) Dark else Light
        ThemePalette.STEEL, ThemePalette.DYNAMIC -> if (dark) BaseDark else BaseLight
        else -> if (dark) darkScheme(palette) else lightScheme(palette)
    }
    return if (dark && pureBlack) scheme.pureBlack() else scheme
}
