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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Feature
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.Holidays
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.HabitInfo
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.ui.ThemeMode
import com.sonnik.pin.ui.ThemePalette
import com.sonnik.pin.ui.staticColorScheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val RU = Locale.forLanguageTag("ru")
// ---------- Привычки ----------

@Composable
internal fun HabitsPane(data: PinData, today: Long, store: PinStore?) {
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val habits = remember(data) { data.entries.filter { !it.deleted && it.entryType == EntryType.HABIT }.sortedBy { it.createdAt } }
    val done = remember(data) {
        data.journal.filter { it.event == JournalEvent.DONE }.groupBy({ it.entryId }, { it.day }).mapValues { it.value.toSet() }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            PaneHeader("Привычки", "Отмечайте дни — щелчок по кружку", "Привычка") { creating = true }
        }
        LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (habits.isEmpty()) item { Hint("Привычек пока нет. Например: «Зарядка», «Читать 20 страниц».") }
            items(habits, key = { it.id }) { h ->
                val info = HabitInfo(h.id, h.rule, h.repeatAnchor ?: today, done[h.id].orEmpty())
                HabitCard(h, info, today, onOpen = { editing = h.id }, onToggle = { day ->
                    store?.commit { Edits.toggleHabit(it, h.id, day, today, System.currentTimeMillis()) }
                })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (store != null && (creating || editing != null)) {
        val id = editing
        EditDialog(if (id == null) "Новая привычка" else "Привычка", 520, onClose = { creating = false; editing = null }) {
            HabitContent(
                data, id,
                onSave = { title, rule, remind ->
                    store.commit { Edits.saveHabit(it, id, title, rule, remind, today, System.currentTimeMillis()).first }
                    creating = false
                    editing = null
                },
                onDelete = { hid -> store.commit { Edits.delete(it, hid, System.currentTimeMillis()) }; editing = null },
                onClose = { creating = false; editing = null },
            )
        }
    }
}

@Composable
internal fun HabitCard(h: EntryEntity, info: HabitInfo, today: Long, onOpen: () -> Unit, onToggle: (Long) -> Unit) {
    val stats = Habits.stats(info, today)
    Card(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.bodyLarge)
                val parts = buildList {
                    add(h.rule?.let(RepeatRule::describe) ?: "каждый день")
                    h.timeMinutes?.let { add("⏰ " + RuText.time(it)) }
                    if (stats.current > 0) add("🔥 ${stats.current}")
                    if (stats.best > stats.current) add("лучшее ${stats.best}")
                    stats.rate30?.let { add("${(it * 100).toInt()}% за 30 дней") }
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Неделя: семь дней до сегодня включительно.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (day in today - 6..today) {
                    val scheduled = Habits.isScheduled(info, day)
                    val isDone = day in info.doneDays
                    val scheme = MaterialTheme.colorScheme
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day == today) scheme.primary else scheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (isDone) scheme.primary else Color.Transparent)
                                .border(2.dp, if (isDone) scheme.primary else if (scheduled) scheme.outline else scheme.outlineVariant, CircleShape)
                                .clickable { onToggle(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) Text("✓", color = scheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitContent(
    data: PinData,
    id: String?,
    onSave: (String, RepeatRule?, Int?) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val habit = id?.let(data.byId::get)
    val codes = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val oldDays = (habit?.rule as? RepeatRule.Weekly)?.days ?: DayOfWeek.entries.toSet()
    var title by remember(id) { mutableStateOf(habit?.title ?: "") }
    var days by remember(id) { mutableStateOf(oldDays) }
    var remind by remember(id) { mutableStateOf(habit?.timeMinutes?.let(RuText::time) ?: "") }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val time = if (remind.isBlank()) null else DateText.parseTime(remind)
    DialogBody(
        buttons = {
            if (habit != null) TextButton(onClick = { onDelete(habit.id) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(onClick = {
                when {
                    title.isBlank() -> error = "Напишите привычку"
                    days.isEmpty() -> error = "Выберите хотя бы один день"
                    remind.isNotBlank() && time == null -> error = "Время — в виде 8:30"
                    else -> {
                        val rule = if (days.size == 7) null else RepeatRule.parse("weekly " + DayOfWeek.entries.filter { it in days }.joinToString(",") { codes[it.value - 1] })
                        onSave(title, rule, time)
                    }
                }
            }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(title, { title = it; error = null }, label = { Text("Привычка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("По каким дням")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { dow ->
                FilterChip(
                    selected = dow in days,
                    onClick = { days = if (dow in days) days - dow else days + dow },
                    label = { Text(dow.getDisplayName(TextStyle.SHORT_STANDALONE, RU)) },
                )
            }
        }
        OutlinedTextField(
            remind, { remind = it; error = null },
            label = { Text("Напомнить в") },
            singleLine = true,
            supportingText = { Text("напоминает телефон; пусто — без напоминания") },
            modifier = Modifier.width(260.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
