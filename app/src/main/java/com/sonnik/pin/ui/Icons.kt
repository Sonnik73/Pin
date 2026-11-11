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

    /** Четыре квадрата — матрица Эйзенхауэра. */
    val Matrix: ImageVector by lazy {
        ImageVector.Builder("Matrix", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 3f); lineTo(11f, 3f); lineTo(11f, 11f); lineTo(3f, 11f); close()
                moveTo(13f, 3f); lineTo(21f, 3f); lineTo(21f, 11f); lineTo(13f, 11f); close()
                moveTo(3f, 13f); lineTo(11f, 13f); lineTo(11f, 21f); lineTo(3f, 21f); close()
                moveTo(13f, 13f); lineTo(21f, 13f); lineTo(21f, 21f); lineTo(13f, 21f); close()
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

    private fun stroked(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** Булавка — важная дата. */
    val Place: ImageVector by lazy {
        stroked("Place") {
            moveTo(12f, 21f); curveTo(12f, 21f, 6f, 15.7f, 6f, 10f); arcTo(6f, 6f, 0f, true, true, 18f, 10f)
            curveTo(18f, 15.7f, 12f, 21f, 12f, 21f); close()
            moveTo(14.2f, 10f); arcTo(2.2f, 2.2f, 0f, true, true, 9.8f, 10f); arcTo(2.2f, 2.2f, 0f, true, true, 14.2f, 10f); close()
        }
    }

    /** Папка — группа важных дат. */
    val Folder: ImageVector by lazy {
        stroked("Folder") {
            moveTo(3f, 7f); arcTo(2f, 2f, 0f, false, true, 5f, 5f); lineTo(9f, 5f); lineTo(11f, 7f); lineTo(19f, 7f)
            arcTo(2f, 2f, 0f, false, true, 21f, 9f); lineTo(21f, 17f); arcTo(2f, 2f, 0f, false, true, 19f, 19f)
            lineTo(5f, 19f); arcTo(2f, 2f, 0f, false, true, 3f, 17f); close()
        }
    }

    /** Колокольчик — напоминание. */
    val Bell: ImageVector by lazy {
        stroked("Bell") {
            moveTo(6f, 16f); lineTo(6f, 11f); arcTo(6f, 6f, 0f, true, true, 18f, 11f); lineTo(18f, 16f)
            lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
            moveTo(10f, 20.5f); arcTo(2f, 2f, 0f, false, false, 14f, 20.5f)
        }
    }
}
