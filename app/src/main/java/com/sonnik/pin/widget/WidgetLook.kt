package com.sonnik.pin.widget

import android.content.Context

/** Вид виджетов — на этом устройстве: непрозрачность фона в процентах (100 — сплошной). */
object WidgetLook {
    private const val PREFS = "widgets"
    private const val KEY_OPACITY = "opacity"
    const val DEFAULT_OPACITY = 95

    fun opacity(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(0, 100)

    fun setOpacity(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_OPACITY, percent.coerceIn(0, 100)).apply()
    }

    /** 0–255 для ImageView.setImageAlpha. */
    fun alpha(context: Context): Int = opacity(context) * 255 / 100
}
