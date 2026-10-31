package com.sonnik.pin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Что лежит в одном дне календаря. */
private sealed interface DayItem {
    val entry: EntryEntity

    /** Открытая задача (или текущий повтор периодической). */
    data class Open(override val entry: EntryEntity) : DayItem

    /** Будущий повтор периодической задачи — ещё не наступил, отметить нельзя. */
    data class Projected(override val entry: EntryEntity) : DayItem

    /** Выполнено в этот день. */
    data class Done(override val entry: EntryEntity, val event: JournalEntity) : DayItem
}

private val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    ui: PinUi,
    doneBetween: (Long, Long) -> Flow<List<JournalEntity>>,
    onOpen: (String) -> Unit,
    onAdd: (Long) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    var selected by rememberSaveable { mutableLongStateOf(ui.today) }
    var monthStart by rememberSaveable { mutableLongStateOf(LocalDate.ofEpochDay(ui.today).withDayOfMonth(1).toEpochDay()) }
    val month = LocalDate.ofEpochDay(monthStart)
    val monthEnd = month.withDayOfMonth(month.lengthOfMonth()).toEpochDay()
    val doneFlow = remember(monthStart) { doneBetween(monthStart, monthEnd) }
    val done by doneFlow.collectAsState(initial = emptyList())
    val items = remember(ui, done, monthStart) { itemsByDay(ui, done, monthStart, monthEnd) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${MONTHS[month.monthValue - 1]} ${month.year}", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { monthStart = month.minusMonths(1).toEpochDay() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                    }
                    IconButton(onClick = { monthStart = month.plusMonths(1).toEpochDay() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(maxOf(selected, ui.today)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Задача на выбранный день")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                MonthGrid(month, ui.today, selected, items) { selected = it }
                val todayMonth = LocalDate.ofEpochDay(ui.today).withDayOfMonth(1).toEpochDay()
                if (monthStart != todayMonth || selected != ui.today) {
                    TextButton(
                        onClick = { monthStart = todayMonth; selected = ui.today },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("К сегодняшнему дню") }
                }
                Text(
                    RuText.headline(selected),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            val dayItems = items[selected].orEmpty()
            if (dayItems.isEmpty()) {
                item {
                    Text(
                        "Ничего не запланировано.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(dayItems, key = { "${it::class.simpleName}-${it.entry.id}-${(it as? DayItem.Done)?.event?.id}" }) { item ->
                DayItemRow(item, ui.today, onOpen = { onOpen(item.entry.id) }, onToggle = onToggle)
            }
        }
    }
}

private fun itemsByDay(ui: PinUi, done: List<JournalEntity>, from: Long, to: Long): Map<Long, List<DayItem>> {
    val result = HashMap<Long, MutableList<DayItem>>()
    fun add(day: Long, item: DayItem) {
        if (day in from..to) result.getOrPut(day) { ArrayList() } += item
    }
    for (entry in ui.entries.values) {
        val due = entry.dueDay ?: continue
        if (entry.entryStatus == TaskStatus.OPEN) {
            add(due, DayItem.Open(entry))
            val schedule = entry.schedule
            if (schedule != null) {
                val start = maxOf(entry.originalDay ?: due, due)
                for (d in RepeatRule.occurrences(schedule.rule, schedule.anchor, maxOf(start + 1, from), to)) {
                    add(d, DayItem.Projected(entry))
                }
            }
        }
    }
    for (event in done) {
        val entry = ui.entries[event.entryId] ?: continue
        add(event.day, DayItem.Done(entry, event))
    }
    return result.mapValues { (_, list) ->
        list.sortedWith(
            compareBy<DayItem> { if (it is DayItem.Done) 1 else 0 }
                .thenByDescending { it.entry.priority }
                .thenBy { it.entry.timeMinutes ?: Int.MAX_VALUE },
        )
    }
}

@Composable
private fun MonthGrid(month: LocalDate, today: Long, selected: Long, items: Map<Long, List<DayItem>>, onSelect: (Long) -> Unit) {
    val first = month.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val weeks = (offset + days + 6) / 7
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEachIndexed { i, name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i >= 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dom = w * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dom in 1..days) {
                            val day = first.withDayOfMonth(dom).toEpochDay()
                            DayCell(dom, day == today, day == selected, items[day].orEmpty()) { onSelect(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(dom: Int, isToday: Boolean, isSelected: Boolean, items: List<DayItem>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = if (isSelected) colors.primary else Color.Transparent
    val textColor = if (isSelected) colors.onPrimary else colors.onSurface
    Column(
        Modifier
            .padding(3.dp)
            .fillMaxSize()
            .background(background, CircleShape)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(dom.toString(), color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isToday) FontWeight.Bold else null)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            val dots = items.take(3).map {
                when (it) {
                    is DayItem.Open -> if (isSelected) colors.onPrimary else colors.primary
                    is DayItem.Projected -> if (isSelected) colors.onPrimary.copy(alpha = 0.6f) else colors.tertiary
                    is DayItem.Done -> if (isSelected) colors.onPrimary.copy(alpha = 0.6f) else colors.outline
                }
            }
            dots.forEach { Box(Modifier.size(4.dp).background(it, CircleShape)) }
        }
    }
}

@Composable
private fun DayItemRow(item: DayItem, today: Long, onOpen: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    val entry = item.entry
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item) {
            is DayItem.Open -> Checkbox(checked = false, onCheckedChange = { onToggle(entry.id, true) })
            is DayItem.Done -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = true, onCheckedChange = null, enabled = false)
            }
            is DayItem.Projected -> Spacer(Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                entry.title,
                textDecoration = if (item is DayItem.Done) TextDecoration.LineThrough else null,
                color = if (item is DayItem.Open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val meta = buildList {
                entry.timeMinutes?.let { add(RuText.time(it)) }
                entry.rule?.let { add("↻ " + RepeatRule.describe(it)) }
                if (item is DayItem.Projected) add("будущий повтор")
                if (item is DayItem.Open && entry.rollovers > 0) add(RuText.rollovers(entry.rollovers))
                if (item is DayItem.Done && !item.event.onTime) add("с опозданием")
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
