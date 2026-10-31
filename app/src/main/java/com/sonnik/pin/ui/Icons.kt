package com.sonnik.pin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Свои значки, которых нет в material-icons-core. */
object PinIcons {
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
