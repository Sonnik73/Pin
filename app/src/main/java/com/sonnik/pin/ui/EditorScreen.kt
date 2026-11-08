package com.sonnik.pin.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.Alignment
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
    lists: List<com.sonnik.pin.data.ListEntity> = emptyList(),
    onSave: (TaskDraft) -> Unit,
    onDelete: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onClose: () -> Unit,
    onFocus: (() -> Unit)? = null,
    onAttach: ((android.net.Uri) -> Unit)? = null,
    onRemoveAttachment: (String) -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(entry?.body ?: "") }
    // У периодической в поле «с какого дня» — якорь повторов, а не текущий повтор.
    var day by rememberSaveable { mutableStateOf(entry?.let { if (it.rule != null) it.repeatAnchor ?: it.dueDay else it.dueDay } ?: newDay) }
    var repeat by rememberSaveable { mutableStateOf(entry?.repeatRule) }
    var pickRepeat by rememberSaveable { mutableStateOf(false) }
    var time by rememberSaveable { mutableStateOf(entry?.timeMinutes) }
    // Напоминания строками: «Напомнить за 2 часа», «за день»… до пяти. У новой задачи — одно «в срок», как раньше.
    var reminders by rememberSaveable { mutableStateOf(entry?.reminderList ?: listOf(0)) }
    var priority by rememberSaveable { mutableStateOf(entry?.entryPriority ?: Priority.NORMAL) }
    var tags by rememberSaveable { mutableStateOf(entry?.tagList?.joinToString(", ") ?: "") }
    var listId by rememberSaveable { mutableStateOf(entry?.listId?.takeIf { id -> lists.any { it.id == id } }) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    // Какую строку напоминания правим (-1 — никакую).
    var pickAdvance by rememberSaveable { mutableStateOf(-1) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val canSave = title.isNotBlank()
    fun save() {
        if (!canSave) return
        onSave(TaskDraft(title, body, day, time, priority, PinRepository.parseTags(tags), RepeatRule.parse(repeat), listId = listId, reminders = reminders))
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
                    AssistChip(
                        onClick = { pickRepeat = true },
                        label = { Text(RepeatRule.parse(repeat)?.let { "↻ " + RepeatRule.describe(it) } ?: "Не повторять") },
                    )
                }
            }

            RemindersSection(
                reminders = reminders,
                hasTime = time != null,
                onEdit = { pickAdvance = it },
                onRemove = { i -> reminders = reminders.filterIndexed { j, _ -> j != i } },
                onAdd = {
                    reminders = reminders + Reminders.suggestNext(reminders)
                    pickAdvance = reminders.lastIndex
                },
            )

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

            ListPicker(lists, listId, { listId = it }, Modifier.fillMaxWidth())

            TagField(tags, { tags = it }, allTags, Modifier.fillMaxWidth())

            AttachmentsSection(
                list = com.sonnik.pin.engine.Attachments.parse(entry?.attachments),
                onAdd = if (entry != null) onAttach else null,
                onOpen = LocalOpenAttachment.current,
                onRemove = { onRemoveAttachment(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )

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
                if (!done && onFocus != null && com.sonnik.pin.engine.Feature.FOCUS.on()) {
                    TextButton(onClick = onFocus) { Text("⏱ Фокус на задаче — 25 минут") }
                }
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

    reminders.getOrNull(pickAdvance)?.let { current ->
        val index = pickAdvance
        AdvanceDialog(
            current,
            onPick = { m ->
                // Такое напоминание уже есть — строка просто сливается с ним.
                reminders = reminders.mapIndexed { j, v -> if (j == index) m else v }.distinct()
                pickAdvance = -1
            },
            onDismiss = { pickAdvance = -1 },
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

/** «Напомнить заранее»: готовые варианты или своё время — дни, часы, минуты. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvanceDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val presets = Reminders.ADVANCE_OPTIONS + listOf(2 * 60, 3 * 60, 24 * 60)
    var custom by rememberSaveable { mutableStateOf(current !in presets) }
    var days by rememberSaveable { mutableStateOf((current / (24 * 60)).takeIf { it > 0 }?.toString() ?: "") }
    var hours by rememberSaveable { mutableStateOf((current % (24 * 60) / 60).takeIf { it > 0 }?.toString() ?: "") }
    var minutes by rememberSaveable { mutableStateOf((current % 60).takeIf { it > 0 }?.toString() ?: "") }
    fun num(s: String) = s.filter(Char::isDigit).take(3).toIntOrNull() ?: 0
    val total = (num(days) * 24 * 60 + num(hours) * 60 + num(minutes)).coerceAtMost(Reminders.MAX_ADVANCE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Напомнить") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { m ->
                        FilterChip(selected = !custom && m == current, onClick = { onPick(m) }, label = { Text(Reminders.describeAdvance(m)) })
                    }
                    FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Своё…") })
                }
                if (custom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("дн", days) { v: String -> days = v }, Triple("ч", hours) { v: String -> hours = v }, Triple("мин", minutes) { v: String -> minutes = v })
                            .forEach { (label, value, set) ->
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { set(it.filter(Char::isDigit).take(3)) },
                                    label = { Text(label) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                    }
                    Text(
                        if (total == 0) "Укажите, за сколько напомнить" else Reminders.describeAdvance(total) + " до срока",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (custom) TextButton(onClick = { onPick(total) }, enabled = total > 0) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Напоминания задачи: строка на каждое, крестик убирает строку, «Добавить напоминание» — новую (до пяти). */
@Composable
private fun RemindersSection(
    reminders: List<Int>,
    hasTime: Boolean,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("Напоминания")
        if (!hasTime) {
            Text(
                "Укажите время задачи — напоминания считаются от него.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reminders.forEachIndexed { i, minutes ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onEdit(i) }, enabled = hasTime, modifier = Modifier.weight(1f)) {
                    Text(
                        "🔔 " + reminderLine(minutes),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                IconButton(onClick = { onRemove(i) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Убрать напоминание")
                }
            }
        }
        if (reminders.size < Reminders.MAX_REMINDERS) {
            TextButton(onClick = onAdd, enabled = hasTime) { Text("＋ Добавить напоминание") }
        }
    }
}

/** «Напомнить за 2 часа», «Напомнить в срок». */
internal fun reminderLine(minutes: Int): String = "Напомнить " + Reminders.describeAdvance(minutes)
