package com.sonnik.pin.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.sonnik.pin.PinApp
import com.sonnik.pin.ui.ThemeMode
import com.sonnik.pin.ui.ThemePalette

/**
 * Цвета виджетов по выбранной в приложении теме: акцент и второй акцент палитры, фон с её оттенком,
 * светлый или тёмный вариант — по режиму темы (как в системе / светлая / тёмная). ARGB-числа.
 */
data class WidgetColors(
    val background: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val second: Int,
    val holiday: Int,
    val dark: Boolean,
) {
    companion object {
        /** Чистый расчёт по палитре: [primary]/[tertiary] — ARGB акцентов для этого режима, [tint] — оттенок фона. */
        fun of(primary: Int, tertiary: Int, tint: Int, tintStrength: Float, dark: Boolean): WidgetColors {
            val base = if (dark) 0xFF1F2227.toInt() else 0xFFFBF8F1.toInt()
            return WidgetColors(
                background = mix(base, tint, (if (dark) 0.05f else 0.06f) * tintStrength.coerceAtMost(3f)),
                text = if (dark) 0xFFE6E2DA.toInt() else 0xFF1F2328.toInt(),
                // Подписи (время, повторы) — на тон темнее прежнего: на просвечивающем фоне виджета читаются увереннее.
                muted = if (dark) 0xFFC6C9D1.toInt() else 0xFF4E525A.toInt(),
                accent = primary,
                second = tertiary,
                holiday = if (dark) 0xFFFF8A80.toInt() else 0xFFD32F2F.toInt(),
                dark = dark,
            )
        }

        /** Смешать два цвета: [t] = 0 — первый, 1 — второй (непрозрачный результат). */
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** Цвета по настройкам оформления приложения. */
        fun current(context: Context): WidgetColors {
            val app = context.applicationContext as? PinApp
            val palette = app?.themeSettings?.palette?.value ?: ThemePalette.PIN
            val mode = app?.themeSettings?.mode?.value ?: ThemeMode.SYSTEM
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            if (palette == ThemePalette.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
                val p = context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
                val t = context.getColor(if (dark) android.R.color.system_accent3_200 else android.R.color.system_accent3_600)
                return of(p, t, context.getColor(android.R.color.system_accent1_500), 1f, dark)
            }
            val primary = if (dark) palette.darkPrimary else palette.lightPrimary
            val tertiary = if (dark) palette.darkTertiary else palette.lightTertiary
            return of(argb(primary), argb(tertiary), argb(palette.tint), palette.tintStrength, dark)
        }

        private fun argb(c: androidx.compose.ui.graphics.Color): Int = android.graphics.Color.argb(
            (c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
        )
    }
}
