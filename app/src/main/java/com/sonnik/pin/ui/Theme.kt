package com.sonnik.pin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Цвета булавки: красная головка на бумаге. */
object PinColors {
    val Red = Color(0xFFC8423A)
    val RedLight = Color(0xFFFF8A7F)
    val Amber = Color(0xFFB7791F)
    val AmberLight = Color(0xFFF0B45A)
    val Paper = Color(0xFFFBF8F1)
    val Ink = Color(0xFF1F2328)
    val Night = Color(0xFF16181C)
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

@Composable
fun PinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
