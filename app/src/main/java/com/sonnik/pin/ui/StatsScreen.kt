package com.sonnik.pin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.StatBucket
import com.sonnik.pin.engine.StatEvent
import com.sonnik.pin.engine.Stats
import com.sonnik.pin.engine.TaskStatus
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class Period(val title: String) { WEEK("Неделя"), MONTH("Месяц"), YEAR("Год") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    ui: PinUi,
    events: List<StatEvent>,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onHabitOpen: (String?) -> Unit = {},
    onHabitToggle: (String) -> Unit = {},
) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    val today = ui.today
    val from = when (period) {
        Period.WEEK -> today - 6
        Period.MONTH -> today - 29
        Period.YEAR -> LocalDate.ofEpochDay(today).withDayOfMonth(1).minusMonths(11).toEpochDay()
    }
    val openToday = ui.board.openCount
    val summary = remember(events, from, today, ui.entries, openToday) {
        Stats.summary(events, from, today, today, { ui.entries[it]?.tagList.orEmpty() }, mapOf(today to openToday))
    }
    val buckets = remember(summary, period) { Stats.buckets(summary.days, byMonth = period == Period.YEAR) }
    val stuck = ui.entries.values
        .filter { it.entryStatus == TaskStatus.OPEN && it.rollovers > 0 && it.dueDay != null }
        .sortedByDescending { it.rollovers }
        .take(5)

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(selected = p == period, onClick = { period = p }, shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size)) {
                        Text(p.title)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Выполнено", "${summary.done}", "из ${summary.planned} запланированных", Modifier.weight(1f))
                Tile("Вовремя", summary.onTimeShare?.let { "${(it * 100).roundToInt()}%" } ?: "—", "не позже исходного дня", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Переносы", "${summary.rolledOver}", "пропущено повторов: ${summary.missed}", Modifier.weight(1f))
                Tile(
                    "Серия",
                    "${summary.currentStreak}",
                    "${RuText.plural(summary.currentStreak, "день", "дня", "дней")} без долгов · лучшая ${summary.bestStreak}",
                    Modifier.weight(1f),
                )
            }

            Card("Привычки") {
                if (ui.habits.isEmpty()) {
                    Text(
                        "Ежедневные действия с сериями: зарядка, вода, чтение. Отмечаются на «Сегодня».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.habits.forEach { h ->
                    HabitRow(ui.entries[h.id]?.title ?: "", h, ui.today, onToggle = { onHabitToggle(h.id) }, onOpen = { onHabitOpen(h.id) })
                }
                androidx.compose.material3.TextButton(onClick = { onHabitOpen(null) }) { Text("+ Привычка") }
            }

            Card("Выполнено и долги") {
                BarChart(buckets, Modifier.fillMaxWidth().height(180.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Legend(MaterialTheme.colorScheme.primary, "выполнено")
                    Legend(MaterialTheme.colorScheme.tertiary, "не сделано в свой день")
                }
            }

            if (summary.byKind.isNotEmpty()) {
                Card("По типам") {
                    HBars(summary.byKind.map { Stats.kindTitle(it.first) to it.second })
                }
            }
            if (summary.byTag.isNotEmpty()) {
                Card("По тегам") {
                    HBars(summary.byTag.take(8).map { (tag, n) -> (if (tag.isEmpty()) "без тега" else "#$tag") to n })
                }
            }
            if (stuck.isNotEmpty()) {
                Card("Самые «зависшие»") {
                    stuck.forEach { e ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(vertical = 6.dp)) {
                            Text(e.title, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(RuText.rollovers(e.rollovers), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (events.none { it.kind != EntryType.HOLIDAY }) {
                Text(
                    "Статистика появится, когда вы начнёте отмечать задачи: каждое выполнение и перенос попадают в журнал.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Tile(title: String, value: String, caption: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
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

/** Столбики: снизу выполненное, сверху долги. Подписи — не чаще, чем помещаются. */
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
        val barWidth = (step * 0.62f).coerceAtMost(28.dp.toPx())
        // Сетка: 0, половина, максимум.
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = chartHeight - chartHeight * f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        drawGridLabel(measurer, "$max", Offset(0f, 0f), labelColor)
        val labelEvery = when {
            buckets.size <= 12 -> 1
            buckets.size <= 31 -> 5
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

private fun DrawScope.drawGridLabel(measurer: TextMeasurer, text: String, at: Offset, color: Color) {
    val layout = measurer.measure(text, TextStyle(fontSize = 10.sp, color = color))
    drawText(layout, topLeft = at)
}

/** Горизонтальные полосы с подписями. */
@Composable
private fun HBars(items: List<Pair<String, Int>>) {
    val max = maxOf(1, items.maxOfOrNull { it.second } ?: 1)
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp), maxLines = 1)
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(color, size = Size(size.width * n / max, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
                Text("$n", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp).padding(start = 8.dp))
            }
        }
    }
}
