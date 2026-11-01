package com.sonnik.pin.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews

/**
 * Разметка под точный размер виджета. С Android 12 лаунчер сообщает список размеров (портрет, альбом,
 * разные сетки) — строим вариант под каждый, и система сама берёт подходящий: ничего не обрезается.
 * На старых версиях — по минимальной ширине и высоте: шрифт чуть мельче, но всё помещается.
 */
internal fun sizedViews(manager: AppWidgetManager, id: Int, build: (widthDp: Int, heightDp: Int) -> RemoteViews): RemoteViews {
    val o = manager.getAppWidgetOptions(id)
    if (Build.VERSION.SDK_INT >= 31) {
        @Suppress("DEPRECATION")
        val sizes = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.filter { it.width > 0 && it.height > 0 }
            ?.distinct()
            ?.take(4)
        if (!sizes.isNullOrEmpty()) {
            return if (sizes.size == 1) {
                build(sizes[0].width.toInt(), sizes[0].height.toInt())
            } else {
                RemoteViews(sizes.associateWith { build(it.width.toInt(), it.height.toInt()) })
            }
        }
    }
    val width = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
    val height = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 180
    return build(width, height)
}
