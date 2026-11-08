package com.sonnik.pin.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.Attachment
import com.sonnik.pin.engine.Attachments
import com.sonnik.pin.engine.CalendarEvent
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Feature
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.Markdown
import com.sonnik.pin.engine.MarkKind
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus
import java.awt.Desktop
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")
// ---------- Календарь ----------

@Composable
internal fun CalendarPane(data: PinData, today: Long) {
    var month by remember { mutableStateOf(LocalDate.ofEpochDay(today).withDayOfMonth(1)) }
    var selected by remember { mutableLongStateOf(today) }
    val first = month
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong()).toEpochDay()
    val gridEnd = gridStart + 41
    val marks = remember(data, today, first) { data.monthMarks(gridStart, gridEnd, today) }
    val events = remember(data, first) { data.monthEvents(gridStart, gridEnd).groupBy { it.day } }
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(monthTitle(first), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { month = LocalDate.ofEpochDay(today).withDayOfMonth(1); selected = today }) { Text("Сегодня") }
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий месяц") }
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.SHORT_STANDALONE, RU).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (d.value >= 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(6.dp),
                    )
                }
            }
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (dow in 0 until 7) {
                        val day = gridStart + week * 7 + dow
                        val date = LocalDate.ofEpochDay(day)
                        DayCell(
                            date = date,
                            inMonth = date.month == first.month,
                            isToday = day == today,
                            isSelected = day == selected,
                            marks = marks[day],
                            events = events[day].orEmpty(),
                            onClick = {
                                selected = day
                                if (date.month != first.month) month = date.withDayOfMonth(1)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        DayDetails(data, selected, today, events[selected].orEmpty(), Modifier.width(380.dp).fillMaxHeight())
    }
}

@Composable
internal fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    marks: com.sonnik.pin.engine.DayMarks?,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val weekend = date.dayOfWeek.value >= 6
    Column(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) scheme.primaryContainer.copy(alpha = 0.6f) else scheme.surfaceContainerLow.copy(alpha = if (inMonth) 1f else 0.4f))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (isToday) scheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        isToday -> scheme.onPrimary
                        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                        marks?.holiday == true || weekend -> scheme.primary
                        else -> scheme.onSurface
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (marks?.important == true) Text("!", color = scheme.primary, style = MaterialTheme.typography.labelLarge)
            if (marks?.birthday == true && (events.isEmpty() || !inMonth)) Text("🎂", style = MaterialTheme.typography.labelMedium)
        }
        if (inMonth) {
            events.take(2).forEach { ev ->
                Text(
                    (if (ev.birthday) "🎂 " else ev.emoji + " ") + ev.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            marks?.dots.orEmpty().forEach { kind ->
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when (kind) {
                            MarkKind.OPEN -> scheme.primary
                            MarkKind.PROJECTED -> scheme.primary.copy(alpha = 0.35f)
                            MarkKind.DONE -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    ),
                )
            }
        }
    }
}

@Composable
internal fun DayDetails(data: PinData, day: Long, today: Long, events: List<CalendarEvent>, modifier: Modifier) {
    val items = remember(data, day, today) {
        data.dayItemsBetween(day, day, today)
            .filter { it.entry.entryType != EntryType.HOLIDAY && it.entry.entryType != EntryType.BIRTHDAY }
            .sortedWith(compareBy({ it.kind.ordinal }, { -it.entry.priority }, { it.entry.timeMinutes ?: Int.MAX_VALUE }))
    }
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text(longDate(day), style = MaterialTheme.typography.titleLarge)
                val rel = RuText.relativeDay(day, today)
                if (day - today in -1L..2L) Text(rel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val actions = LocalActions.current
                TextButton(onClick = { actions.add(maxOf(day, today)) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (day < today) "+ Задача на сегодня" else "+ Задача на этот день")
                }
            }
            items(events) { ev -> Text(eventText(ev), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
            if (items.isEmpty() && events.isEmpty()) {
                item { Text("Ничего не запланировано", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(items, key = { "${it.kind}:${it.entry.id}" }) { item ->
                val snap = item.entry.toSnapshot()?.copy(
                    currentDay = day,
                    status = if (item.kind == MarkKind.DONE) TaskStatus.DONE else TaskStatus.OPEN,
                    rollovers = if (item.kind == MarkKind.OPEN) item.entry.rollovers else 0,
                ) ?: return@items
                Column {
                    if (item.kind == MarkKind.PROJECTED) {
                        Text("повтор", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                    }
                    TaskCard(
                        snap, item.entry, data, today,
                        canToggle = item.kind == MarkKind.OPEN || (item.kind == MarkKind.DONE && item.entry.completedDay == day),
                    )
                }
            }
        }
    }
}
