package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.Focus
import com.sonnik.pin.engine.FocusPhase
import com.sonnik.pin.engine.FocusState
import com.sonnik.pin.engine.StatEvent
import kotlinx.coroutines.delay

/** Минуты фокуса за день из журнала. */
fun focusMinutes(events: List<StatEvent>, day: Long): Int = events.filter { it.event == "FOCUS" && it.day == day }.sumOf { it.rollovers }

/** Строка «Фокус 12:34 — задача» над списком «Сегодня», пока идёт таймер. */
@Composable
fun FocusBanner(state: FocusState, title: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏱ ", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(state.phase.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                title?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
            }
            Text(Focus.format(state.remainingMs(now)), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Экран фокус-таймера: большой отсчёт, кольцо прогресса, «Старт», «Стоп». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    state: FocusState?,
    taskTitle: String?,
    todayMinutes: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фокус") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                taskTitle ?: "Без задачи",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val total = state?.let { it.endsAt - it.startedAt } ?: (25 * 60_000L)
            val remaining = state?.remainingMs(now) ?: total
            val ring = if (state?.phase == FocusPhase.WORK || state == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            val track = MaterialTheme.colorScheme.surfaceVariant
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    val sweep = 360f * (remaining.toFloat() / total)
                    drawArc(ring, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Focus.format(remaining), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Light)
                    Text(state?.phase?.title ?: "25 минут", style = MaterialTheme.typography.titleMedium, color = ring)
                }
            }
            if (state == null) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Начать 25 минут") }
            } else {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.phase == FocusPhase.WORK) "Остановить (минуты засчитаются)" else "Закончить перерыв")
                }
                if (state.phase != FocusPhase.WORK) {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Сразу следующий отрезок") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Сегодня в фокусе: $todayMinutes мин" + (state?.round?.takeIf { it > 0 }?.let { " · отрезков подряд: $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "25 минут работы, 5 минут перерыв, каждый четвёртый перерыв — 15 минут. Отсчёт виден в уведомлении, " +
                    "даже если закрыть Pin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
