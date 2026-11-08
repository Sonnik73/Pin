package com.sonnik.pin.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Feature
import com.sonnik.pin.engine.Focus
import com.sonnik.pin.engine.FocusPhase
import com.sonnik.pin.engine.FocusState
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.LogicalDay
import com.sonnik.pin.engine.Matrix
import com.sonnik.pin.engine.MatrixItem
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.Quadrant
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.StatBucket
import com.sonnik.pin.engine.StatEvent
import com.sonnik.pin.engine.Stats
import com.sonnik.pin.engine.TaskStatus
import java.time.Clock
import java.time.LocalDate
import java.util.prefs.Preferences
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ---------- Итоги ----------

internal fun PinData.statEvents(): List<StatEvent> =
    journal.map { StatEvent(it.entryId, it.event, it.day, EntryType.parse(it.kind) ?: EntryType.ONE_TIME, it.priority, it.rollovers, it.onTime) }

internal fun focusMinutes(events: List<StatEvent>, day: Long): Int =
    events.filter { it.event == JournalEvent.FOCUS && it.day == day }.sumOf { it.rollovers }

private enum class Period(val title: String) { WEEK("Неделя"), MONTH("Месяц"), YEAR("Год") }

/** «Итоги» — те же цифры, что на телефоне и в Крепости. */
@Composable
internal fun StatsPane(data: PinData, today: Long, onFocus: () -> Unit = {}) {
    val actions = LocalActions.current
    var period by remember { mutableStateOf(Period.WEEK) }
    val from = when (period) {
        Period.WEEK -> today - 6
        Period.MONTH -> today - 29
        Period.YEAR -> LocalDate.ofEpochDay(today).withDayOfMonth(1).minusMonths(11).toEpochDay()
    }
    val events = remember(data) { data.statEvents() }
    val openToday = remember(data, today) { data.board(today).openCount }
    val summary = remember(events, from, today, openToday) {
        Stats.summary(events, from, today, today, { data.byId[it]?.tagList.orEmpty() }, mapOf(today to openToday))
    }
    val buckets = remember(summary, period) { Stats.buckets(summary.days, byMonth = period == Period.YEAR) }
    val stuck = remember(data, today) {
        data.entries.filter { !it.deleted && it.entryStatus == TaskStatus.OPEN && it.dueDay != null }
            .map { Edits.rolled(it, today) }.filter { it.rollovers > 0 }.sortedByDescending { it.rollovers }.take(5)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Итоги", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                Period.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { period = p }, label = { Text(p.title) }, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Выполнено", "${summary.done}", "из ${summary.planned} запланированных", Modifier.weight(1f))
                Tile("Вовремя", summary.onTimeShare?.let { "${(it * 100).roundToInt()}%" } ?: "—", "не позже исходного дня", Modifier.weight(1f))
                Tile("Переносы", "${summary.rolledOver}", "пропущено повторов: ${summary.missed}", Modifier.weight(1f))
                Tile(
                    "Серия", "${summary.currentStreak}",
                    "${RuText.plural(summary.currentStreak, "день", "дня", "дней")} без долгов · лучшая ${summary.bestStreak}",
                    Modifier.weight(1f),
                )
            }
            StatCard("Выполнено и долги") {
                BarChart(buckets, Modifier.fillMaxWidth().height(200.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Legend(MaterialTheme.colorScheme.primary, "выполнено")
                    Legend(MaterialTheme.colorScheme.tertiary, "не сделано в свой день")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (Feature.FOCUS.on()) StatCard("Фокус") {
                        val week = (today - 6..today).sumOf { focusMinutes(events, it) }
                        Text("Сегодня ${focusMinutes(events, today)} мин · за 7 дней $week мин", style = MaterialTheme.typography.bodyLarge)
                        androidx.compose.material3.TextButton(onClick = onFocus) { Text("⏱ Начать фокус") }
                    }
                    if (summary.byKind.isNotEmpty()) StatCard("По типам") { HBars(summary.byKind.map { Stats.kindTitle(it.first) to it.second }) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (summary.byTag.isNotEmpty()) StatCard("По тегам") {
                        HBars(summary.byTag.take(8).map { (tag, n) -> (if (tag.isEmpty()) "без тега" else "#$tag") to n })
                    }
                    if (stuck.isNotEmpty()) StatCard("Самые «зависшие»") {
                        stuck.forEach { e ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { actions.open(e) }.padding(vertical = 6.dp)) {
                                Text(e.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(RuText.rollovers(e.rollovers), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (events.none { it.kind != EntryType.HOLIDAY && it.event != JournalEvent.FOCUS }) {
                Text(
                    "Итоги появятся, когда вы начнёте отмечать задачи: каждое выполнение и перенос попадают в журнал.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tile(title: String, value: String, caption: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Столбики: снизу выполненное, сверху долги. */
@Composable
private fun BarChart(buckets: List<StatBucket>, modifier: Modifier) {
    val done = MaterialTheme.colorScheme.primary
    val debt = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (buckets.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val max = maxOf(1, buckets.maxOf { it.done + it.debts })
        val step = size.width / buckets.size
        val barWidth = (step * 0.62f).coerceAtMost(32.dp.toPx())
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = chartHeight - chartHeight * f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        drawText(measurer.measure("$max", TextStyle(fontSize = 10.sp, color = labelColor)), topLeft = Offset(0f, 0f))
        val labelEvery = when {
            buckets.size <= 12 -> 1
            buckets.size <= 31 -> 3
            else -> 10
        }
        buckets.forEachIndexed { i, b ->
            val x = step * i + (step - barWidth) / 2
            val doneH = chartHeight * b.done / max
            val debtH = chartHeight * b.debts / max
            val radius = CornerRadius(4.dp.toPx())
            if (b.done > 0) drawRoundRect(done, Offset(x, chartHeight - doneH), Size(barWidth, doneH), radius)
            if (b.debts > 0) drawRoundRect(debt, Offset(x, chartHeight - doneH - debtH), Size(barWidth, debtH), radius)
            if (i % labelEvery == 0 || i == buckets.lastIndex) {
                val layout = measurer.measure(b.label, TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(layout, topLeft = Offset(step * i + (step - layout.size.width) / 2, chartHeight + 3.dp.toPx()))
            }
        }
    }
}

@Composable
private fun HBars(items: List<Pair<String, Int>>) {
    val max = maxOf(1, items.maxOfOrNull { it.second } ?: 1)
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(color, size = Size(size.width * n / max, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
                Text("$n", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp).padding(start = 8.dp))
            }
        }
    }
}

// ---------- Матрица Эйзенхауэра ----------

/** Открытые задачи и планы для матрицы — с досчитанным переносом. */
internal fun PinData.matrixItems(today: Long): List<MatrixItem> = entries
    .filter { !it.deleted && !it.disabled && (it.entryStatus == TaskStatus.OPEN || (it.entryStatus == TaskStatus.DONE && it.completedDay == today)) }
    .filter {
        when (it.entryType) {
            EntryType.ONE_TIME, EntryType.RECURRING -> it.dueDay != null
            EntryType.PLAN -> true
            else -> false
        }
    }
    .map { Edits.rolled(it, today) }
    .map { MatrixItem(it.id, it.entryType, it.dueDay, it.entryPriority, it.rollovers, it.timeMinutes, done = it.entryStatus == TaskStatus.DONE) }

/** Матрица: срочно — сегодня, завтра и перенесённое; важно — высокий приоритет. Звёздочка меняет важность. */
@Composable
internal fun MatrixPane(data: PinData, today: Long, onImportant: (String, Boolean) -> Unit) {
    val board = remember(data, today) { Matrix.build(data.matrixItems(today), today) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Матрица Эйзенхауэра", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Срочно — на сегодня, завтра или перенесённое. Важно — высокий приоритет: ☆ делает задачу важной.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(Quadrant.DO to Quadrant.SCHEDULE, Quadrant.QUICK to Quadrant.LATER).forEach { (left, right) ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuadrantCard(left, board.getValue(left), data, today, onImportant, Modifier.weight(1f))
                QuadrantCard(right, board.getValue(right), data, today, onImportant, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuadrantCard(q: Quadrant, items: List<MatrixItem>, data: PinData, today: Long, onImportant: (String, Boolean) -> Unit, modifier: Modifier) {
    val actions = LocalActions.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = when (q) {
        Quadrant.DO -> ListColor.RED
        Quadrant.SCHEDULE -> ListColor.BLUE
        Quadrant.QUICK -> ListColor.AMBER
        Quadrant.LATER -> ListColor.GRAY
    }.let { Color(if (dark) it.dark else it.light) }
    Surface(color = base.copy(alpha = if (dark) 0.16f else 0.10f), shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(top = 12.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(q.title, style = MaterialTheme.typography.titleMedium, color = base, modifier = Modifier.weight(1f))
                Text("${items.count { !it.done }}", style = MaterialTheme.typography.titleMedium, color = base)
            }
            Text(q.hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp))
            if (items.isEmpty()) {
                Text(if (q == Quadrant.DO) "Пусто — хорошо." else "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
            }
            LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
                items(items, key = { it.id }) { item ->
                    val entry = data.byId[item.id] ?: return@items
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.open(entry) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.type == EntryType.PLAN) {
                            Text("💡", modifier = Modifier.padding(horizontal = 4.dp))
                        } else {
                            Box(
                                Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (item.done) base else Color.Transparent)
                                    .then(if (item.done) Modifier else Modifier.background(Color.Transparent))
                                    .clickable { actions.toggle(entry, !item.done) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (item.done) "✓" else "☐", color = if (item.done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            )
                            val meta = listOfNotNull(
                                item.day?.let { RuText.relativeDay(it, today) },
                                item.timeMinutes?.let { RuText.time(it) },
                                item.rollovers.takeIf { it > 0 }?.let { RuText.rollovers(it) },
                                entry.listId?.let { data.listsById[it]?.name },
                            )
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(
                            if (q.important) "★" else "☆",
                            style = MaterialTheme.typography.titleMedium,
                            color = base,
                            modifier = Modifier.clip(CircleShape).clickable { onImportant(item.id, !q.important) }.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- Фокус-таймер ----------

/**
 * Помодоро на компьютере: состояние — в реестре (переживает закрытие программы), конец фазы — уведомление Windows.
 * Минуты рабочей фазы пишутся в журнал Pin.zip (событие FOCUS), как на телефоне.
 */
class FocusTimer(private val onWorked: (taskId: String?, minutes: Int, startedAt: Long) -> Unit) {
    private val prefs = Preferences.userRoot().node("com/sonnik/pin/focus")
    var state by mutableStateOf(load())
        private set

    private fun load(): FocusState? {
        val phase = prefs.get("phase", null)?.let { p -> FocusPhase.entries.firstOrNull { it.name == p } } ?: return null
        return FocusState(phase, prefs.getLong("started", 0), prefs.getLong("ends", 0), prefs.getInt("round", 0), prefs.get("task", null))
    }

    private fun save(s: FocusState?) {
        if (s == null) {
            prefs.clear()
        } else {
            prefs.put("phase", s.phase.name)
            prefs.putLong("started", s.startedAt)
            prefs.putLong("ends", s.endsAt)
            prefs.putInt("round", s.round)
            if (s.taskId != null) prefs.put("task", s.taskId) else prefs.remove("task")
        }
        runCatching { prefs.flush() }
        state = s
    }

    fun start(taskId: String?) {
        save(Focus.start(System.currentTimeMillis(), taskId ?: state?.taskId, state?.round ?: 0))
    }

    fun stop() {
        val s = state ?: return
        val step = Focus.finish(s, System.currentTimeMillis())
        if (s.phase == FocusPhase.WORK) onWorked(s.taskId, step.workedMinutes, s.startedAt)
        save(null)
    }

    /** Проверка конца фазы: вызывать раз в секунду. Возвращает текст для уведомления, если фаза закончилась. */
    fun tick(now: Long = System.currentTimeMillis()): String? {
        val s = state ?: return null
        if (now < s.endsAt) return null
        val step = Focus.finish(s, now)
        if (s.phase == FocusPhase.WORK) onWorked(s.taskId, step.workedMinutes, s.startedAt)
        save(step.next)
        return when (step.next?.phase) {
            FocusPhase.BREAK -> "Отрезок закончен — перерыв 5 минут"
            FocusPhase.LONG_BREAK -> "Отрезок закончен — длинный перерыв 15 минут"
            else -> "Перерыв закончен — можно начинать следующий отрезок"
        }
    }

    companion object {
        fun dayOf(at: Long): Long = DesktopPrefs.logicalDay().dayOf(at)
    }
}

@Composable
internal fun FocusPane(data: PinData, today: Long, timer: FocusTimer?) {
    val state = timer?.state
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val events = remember(data) { data.statEvents() }
    val taskTitle = state?.taskId?.let { data.byId[it]?.title }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Фокус", style = MaterialTheme.typography.headlineLarge)
            Text(
                taskTitle ?: "Без задачи — выберите «⏱ Фокус» в меню задачи",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val total = state?.let { it.endsAt - it.startedAt } ?: (25 * 60_000L)
            val remaining = state?.remainingMs(now) ?: total
            val ring = if (state == null || state.phase == FocusPhase.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            val track = MaterialTheme.colorScheme.surfaceVariant
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(ring, -90f, 360f * (remaining.toFloat() / total), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Focus.format(remaining), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Light)
                    Text(state?.phase?.title ?: "25 минут", style = MaterialTheme.typography.titleMedium, color = ring)
                }
            }
            if (state == null) {
                Button(onClick = { timer?.start(null) }, modifier = Modifier.fillMaxWidth()) { Text("Начать 25 минут") }
            } else {
                OutlinedButton(onClick = { timer.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.phase == FocusPhase.WORK) "Остановить (минуты засчитаются)" else "Закончить перерыв")
                }
                if (state.phase != FocusPhase.WORK) {
                    Button(onClick = { timer.start(state.taskId) }, modifier = Modifier.fillMaxWidth()) { Text("Сразу следующий отрезок") }
                }
            }
            Text(
                "Сегодня в фокусе: ${focusMinutes(events, today)} мин" + (state?.round?.takeIf { it > 0 }?.let { " · отрезков подряд: $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "25 минут работы, 5 минут перерыв, каждый четвёртый перерыв — 15 минут. Конец отрезка — уведомлением Windows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

