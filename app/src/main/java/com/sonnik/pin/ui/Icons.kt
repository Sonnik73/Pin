package com.sonnik.pin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Свои значки, которых нет в material-icons-core. */
object PinIcons {
    /** Микрофон — голосовое добавление. */
    val Mic: ImageVector by lazy {
        ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // Капсула микрофона.
                moveTo(12f, 2f); curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f); lineTo(9f, 11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                lineTo(15f, 5f); curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f); close()
                // Дуга и ножка.
                moveTo(17.3f, 11f); curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f); curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
                lineTo(5f, 11f); curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f); lineTo(11f, 21f); lineTo(13f, 21f)
                lineTo(13f, 17.72f); curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f); close()
            }
        }.build()
    }

    /** Столбики графика — вкладка статистики. */
    val Chart: ImageVector by lazy {
        ImageVector.Builder("Chart", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 20f); lineTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 20f); close()
                moveTo(10f, 20f); lineTo(10f, 5f); lineTo(14f, 5f); lineTo(14f, 20f); close()
                moveTo(16f, 20f); lineTo(16f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); close()
            }
        }.build()
    }
}
