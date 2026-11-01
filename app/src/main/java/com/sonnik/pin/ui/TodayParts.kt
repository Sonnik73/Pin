package com.sonnik.pin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.DayProgress

/** Кольцо прогресса: дорожка и дуга по доле [fraction]; дуга плавно доезжает при изменении. */
@Composable
fun ProgressRing(fraction: Float, size: Dp, stroke: Dp, color: Color, track: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val style = Stroke(width = w, cap = StrokeCap.Round)
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = style)
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = style)
        }
        content()
    }
}

/** Шапка «Сегодня»: кольцо «сделано из», что осталось. */
@Composable
fun ProgressHeader(progress: DayProgress, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val done = progress.allDone
    Surface(
        color = if (done) colors.primaryContainer else colors.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = progress.fraction,
                size = 56.dp,
                stroke = 7.dp,
                color = colors.primary,
                track = colors.primary.copy(alpha = 0.15f),
            ) {
                Text(
                    if (done) "✓" else "${(progress.fraction * 100).toInt()}%",
                    style = if (done) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    progress.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) colors.onPrimaryContainer else colors.onSurface,
                )
                val sub = if (done) "Свободный вечер — заслужили 📌" else progress.subtitle
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Заголовок раздела «Сегодня». Прилипает к верху при прокрутке (фон — как у экрана),
 * у сворачиваемых — стрелка и нажатие.
 */
@Composable
fun SectionHeader(
    title: String,
    count: Int,
    accent: Boolean,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "arrow")
    Row(
        Modifier
            .fillMaxWidth()
            .then(Modifier.clickable(enabled = collapsible, onClick = onToggle))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$title · $count",
            style = MaterialTheme.typography.titleSmall,
            color = if (accent) colors.tertiary else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (collapsible) {
            Text("▾", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.rotate(rotation))
        }
    }
}
