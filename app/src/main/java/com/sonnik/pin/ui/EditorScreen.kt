package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.Reminders
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskStatus

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Редактор задачи: [entry] = null — новая задача на [newDay]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    entry: EntryEntity?,
    today: Long,
    newDay: Long = today,
    allTags: Collection<String> = emptyList(),
    onSave: (TaskDraft) -> Unit,
    onDelete: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(entry?.body ?: "") }
    // У периодической в поле «с какого дня» — якорь повторов, а не текущий повтор.
    var day by rememberSaveable { mutableStateOf(entry?.let { if (it.rule != null) it.repeatAnchor ?: it.dueDay else it.dueDay } ?: newDay) }
    var repeat by rememberSaveable { mutableStateOf(entry?.repeatRule) }
    var pickRepeat by rememberSaveable { mutableStateOf(false) }
    var time by rememberSaveable { mutableStateOf(entry?.timeMinutes) }
    var remindBefore by rememberSaveable { mutableStateOf(entry?.remindBefore ?: 0) }
    var priority by rememberSaveable { mutableStateOf(entry?.entryPriority ?: Priority.NORMAL) }
    var tags by rememberSaveable { mutableStateOf(entry?.tagList?.joinToString(", ") ?: "") }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val canSave = title.isNotBlank()
    fun save() {
        if (!canSave) return
        onSave(TaskDraft(title, body, day, time, priority, PinRepository.parseTags(tags), RepeatRule.parse(repeat), remindBefore = if (time == null) 0 else remindBefore))
        onClose()
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) "Новая задача" else "Задача") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    }
                    TextButton(onClick = ::save, enabled = canSave) { Text("Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Что сделать") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(if (repeat != null) "Повторять, начиная с" else "Когда")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = day == today, onClick = { day = today }, label = { Text("Сегодня") })
                    FilterChip(selected = day == today + 1, onClick = { day = today + 1 }, label = { Text("Завтра") })
                    AssistChip(
                        onClick = { pickDate = true },
                        label = { Text(if (day == today || day == today + 1) "Другой день…" else RuText.relativeDayWithDate(day, today)) },
                    )
                    AssistChip(
                        onClick = { pickTime = true },
                        label = { Text(time?.let { "в " + RuText.time(it) } ?: "Без времени") },
                    )
                    if (time != null) {
                        // Нажатие перебирает варианты: в срок → за 5 → за 15 → за 30 → за час.
                        AssistChip(
                            onClick = {
                                val options = Reminders.ADVANCE_OPTIONS
                                remindBefore = options[(options.indexOf(remindBefore) + 1).mod(options.size)]
                            },
                            label = { Text("🔔 " + Reminders.describeAdvance(remindBefore)) },
                        )
                    }
                    AssistChip(
                        onClick = { pickRepeat = true },
                        label = { Text(RepeatRule.parse(repeat)?.let { "↻ " + RepeatRule.describe(it) } ?: "Не повторять") },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Приоритет")
                val options = listOf(Priority.LOW to "Низкий", Priority.NORMAL to "Обычный", Priority.HIGH to "Важный")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (p, label) ->
                        SegmentedButton(
                            selected = priority == p,
                            onClick = { priority = p },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
            }

            TagField(tags, { tags = it }, allTags, Modifier.fillMaxWidth())

            MarkdownField(body = body, onChange = { body = it })

            if (entry != null) {
                val originalDay = entry.originalDay
                val info = buildList {
                    if (originalDay != null && originalDay != entry.dueDay) add("изначально на ${RuText.relativeDay(originalDay, today)}")
                    if (entry.rollovers > 0) add(RuText.rollovers(entry.rollovers))
                    entry.completedDay?.let { add("выполнено ${RuText.relativeDay(it, today)}") }
                }
                if (info.isNotEmpty()) {
                    Text(
                        info.joinToString(" · ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val done = entry.entryStatus == TaskStatus.DONE
                TextButton(onClick = { onToggleDone(!done); onClose() }) {
                    Text(if (done) "Вернуть в невыполненные" else "Отметить выполненной")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Math.floorDiv(it, DAY_MILLIS) }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (pickTime) {
        val initial = time ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Время") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { time = state.hour * 60 + state.minute; pickTime = false }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { time = null; pickTime = false }) { Text("Без времени") }
            },
        )
    }

    if (pickRepeat) {
        RepeatDialog(
            current = RepeatRule.parse(repeat),
            day = day,
            onPick = { repeat = it?.let(RepeatRule::format); pickRepeat = false },
            onDismiss = { pickRepeat = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить задачу?") },
            text = { Text("«${entry?.title}» исчезнет из списка. История выполнений в статистике сохранится.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(); onClose() }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
