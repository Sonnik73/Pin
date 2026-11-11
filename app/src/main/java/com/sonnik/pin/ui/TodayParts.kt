package com.sonnik.pin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
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
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (accent) colors.tertiary else colors.onSurface,
        )
        Text(
            "$count",
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (collapsible) {
            Text("▾", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.rotate(rotation))
        }
    }
}

/**
 * Свайп по задаче: вправо — выполнено (или вернуть, если уже сделана), влево — на завтра.
 * [onDone] = null — свайп вправо выключен (Настройки → «Функции»).
 * Карточка возвращается на место, а задача уезжает в свой раздел уже как обычное изменение данных.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SwipeTask(
    done: Boolean,
    onDone: ((Boolean) -> Unit)?,
    onTomorrow: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> onDone?.invoke(!done)
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> onTomorrow?.invoke()
                else -> {}
            }
            false // карточка не исчезает — её переложит обновление списка
        },
        positionalThreshold = { it * 0.35f },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = onDone != null,
        enableDismissFromEndToStart = onTomorrow != null,
        backgroundContent = {
            val toRight = state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
            val colors = MaterialTheme.colorScheme
            val bg = when {
                state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.Settled -> Color.Transparent
                toRight -> colors.primaryContainer
                else -> colors.tertiaryContainer
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .background(bg, RoundedCornerShape(14.dp)),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (bg != Color.Transparent) {
                    Text(
                        if (toRight) (if (done) "↺ Вернуть" else "✓ Готово") else "Завтра →",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (toRight) colors.onPrimaryContainer else colors.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        },
    ) { content() }
}

/**
 * Конфетти на весь экран, когда сделано всё: ~70 бумажек падают и крутятся 1,8 с.
 * [key] меняется — салют запускается заново.
 */
@Composable
fun Confetti(key: Int, modifier: Modifier = Modifier) {
    if (key == 0) return
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary, Color(0xFFF2B33D), Color(0xFF4CAF50), Color(0xFF42A5F5))
    val pieces = androidx.compose.runtime.remember(key) {
        val r = kotlin.random.Random(key)
        List(70) {
            ConfettiPiece(
                x = r.nextFloat(),
                speed = 0.55f + r.nextFloat() * 0.6f,
                drift = (r.nextFloat() - 0.5f) * 0.35f,
                spin = (r.nextFloat() - 0.5f) * 1080f,
                delay = r.nextFloat() * 0.25f,
                color = palette[r.nextInt(palette.size)],
                w = 6f + r.nextFloat() * 6f,
            )
        }
    }
    val progress = androidx.compose.runtime.remember(key) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1800))
    }
    if (progress.value >= 1f) return
    Canvas(modifier) {
        val t = progress.value
        for (p in pieces) {
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val y = -20f + (size.height + 40f) * local * p.speed * 1.4f
            val x = size.width * (p.x + p.drift * local)
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotateDraw(p.spin * local, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                drawRect(
                    p.color.copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(x - p.w / 2, y - p.w),
                    size = androidx.compose.ui.geometry.Size(p.w * density / 2, p.w * density),
                )
            }
        }
    }
}

private data class ConfettiPiece(val x: Float, val speed: Float, val drift: Float, val spin: Float, val delay: Float, val color: Color, val w: Float)
