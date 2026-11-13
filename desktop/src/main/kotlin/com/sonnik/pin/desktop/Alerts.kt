package com.sonnik.pin.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Настойчивые напоминания: висят в углу экрана поверх всех программ, пока не нажмёшь «Готово», «Через 10 мин»
 * или «Закрыть». «Готово» выполняет окно Pin ([onDone] задаёт App); «Через 10 мин» возвращает карточку позже.
 */
object Alerts {
    /** Сколько карточек видно сразу; остальные — «ещё N». */
    const val SHOWN = 3
    const val SNOOZE_MINUTES = 10

    val items = mutableStateListOf<Alert>()
    var onDone: ((Alert) -> Unit)? = null
    private val snoozed = mutableListOf<Pair<Alert, Long>>()
    var onOpen: (() -> Unit)? = null

    fun push(list: List<Alert>) {
        val fresh = list.filter { a -> items.none { it.title == a.title && it.text == a.text && it.taskId == a.taskId } }
        if (fresh.isEmpty()) return
        items.addAll(fresh)
        java.awt.Toolkit.getDefaultToolkit().beep()
    }

    fun dismiss(a: Alert) {
        items.remove(a)
    }

    /** Убрать карточку и вернуть её через [SNOOZE_MINUTES] минут. */
    fun snooze(a: Alert, now: Long) {
        items.remove(a)
        synchronized(snoozed) { snoozed += a to now + SNOOZE_MINUTES * 60_000L }
    }

    /** Отложенные карточки, чьё время пришло; забирает их из очереди. */
    fun dueSnoozed(now: Long): List<Alert> = synchronized(snoozed) {
        val due = snoozed.filter { it.second <= now }
        snoozed.removeAll(due)
        due.map { it.first }
    }

    /** Ближайшее время отложенной карточки (не забирая её) — чтобы цикл напоминаний проснулся вовремя. */
    fun nextSnoozeAt(now: Long): Long? = synchronized(snoozed) { snoozed.minOfOrNull { it.second } }?.takeIf { it > now }
}

/** Окно напоминаний в правом нижнем углу (над панелью задач). Пустое — не показывается. */
@Composable
fun ApplicationScope.AlertsWindow(icon: Painter?) {
    if (Alerts.items.isEmpty()) return
    val shown = Alerts.items.take(Alerts.SHOWN)
    val more = Alerts.items.size - shown.size
    val width = 380
    val height = 24 + shown.size * 136 + if (more > 0) 44 else 0
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val state = rememberWindowState(
        position = WindowPosition((bounds.x + bounds.width - width - 12).dp, (bounds.y + bounds.height - height - 12).dp),
        size = DpSize(width.dp, height.dp),
    )
    // Карточек стало больше или меньше — окно растёт вверх, нижний край на месте.
    state.size = DpSize(width.dp, height.dp)
    state.position = WindowPosition((bounds.x + bounds.width - width - 12).dp, (bounds.y + bounds.height - height - 12).dp)
    Window(
        onCloseRequest = { Alerts.items.clear() },
        state = state,
        title = "Pin — напоминание",
        icon = icon,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
    ) {
        PinTheme {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
                if (more > 0) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ещё $more", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { Alerts.items.clear() }) { Text("Закрыть все") }
                        }
                    }
                }
                shown.forEach { a -> AlertCard(a) }
            }
        }
    }
}

@Composable
private fun AlertCard(a: Alert) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer, shadowElevation = 8.dp, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.title, style = MaterialTheme.typography.labelLarge, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (a.kind) {
                    Alert.Kind.TASK -> {
                        Button(onClick = { Alerts.onDone?.invoke(a); Alerts.dismiss(a) }) { Text("Готово") }
                        OutlinedButton(onClick = { Alerts.snooze(a, System.currentTimeMillis()) }) { Text("Через ${Alerts.SNOOZE_MINUTES} мин") }
                    }
                    Alert.Kind.HABIT -> Button(onClick = { Alerts.onDone?.invoke(a); Alerts.dismiss(a) }) { Text("Отметить") }
                    Alert.Kind.INFO -> OutlinedButton(onClick = { Alerts.onOpen?.invoke(); Alerts.dismiss(a) }) { Text("Открыть Pin") }
                }
                Spacer(Modifier.weight(1f).width(0.dp))
                TextButton(onClick = { Alerts.dismiss(a) }) { Text("Закрыть") }
            }
        }
    }
}

/**
 * Pin запускается один раз: вторая копия (ярлык, автозапуск) не открывает своё окно и трей,
 * а просит уже работающую показать окно — и закрывается.
 */
class SingleInstance(private val dir: File = LocalCopy.defaultDir()) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private val request get() = File(dir, "show.request")

    /** true — эта копия первая и держит замок до выхода. */
    fun acquire(): Boolean {
        dir.mkdirs()
        val ch = RandomAccessFile(File(dir, "instance.lock"), "rw").channel
        val l = try {
            ch.tryLock()
        } catch (e: OverlappingFileLockException) {
            null
        } catch (e: java.io.IOException) {
            null
        }
        if (l == null) {
            ch.close()
            return false
        }
        channel = ch
        lock = l
        return true
    }

    /** Вторая копия: попросить первую показать окно. */
    fun requestShow() {
        runCatching { dir.mkdirs(); request.writeText(System.currentTimeMillis().toString()) }
    }

    /** Время последней просьбы показать окно, если она новее [since]. */
    fun showRequestedAfter(since: Long): Long? = request.takeIf { it.isFile }?.lastModified()?.takeIf { it > since }

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
    }
}
