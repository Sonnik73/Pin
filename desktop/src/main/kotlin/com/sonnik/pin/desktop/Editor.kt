package com.sonnik.pin.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.Attachments
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Markdown
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskStatus
import java.awt.FileDialog
import java.io.File
import java.time.LocalDate

/** «4.11.2026» ↔ день. */
internal object DateText {
    fun format(day: Long): String = LocalDate.ofEpochDay(day).let { "${it.dayOfMonth}.%02d.${it.year}".format(it.monthValue) }

    fun parse(text: String, today: Long): Long? {
        val m = Regex("""^\s*(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?\s*$""").matchEntire(text) ?: return null
        val (d, mo, y) = m.destructured
        val year = when {
            y.isEmpty() -> LocalDate.ofEpochDay(today).year
            y.length == 2 -> 2000 + y.toInt()
            else -> y.toInt()
        }
        return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()).toEpochDay() }.getOrNull()
    }

    fun parseTime(text: String): Int? {
        val m = Regex("""^\s*(\d{1,2})(?:[:.](\d{2}))?\s*$""").matchEntire(text) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }
}

/** Варианты повтора для окна правки — от выбранного дня. */
internal fun repeatChoices(day: Long, current: RepeatRule?): List<Pair<String, RepeatRule?>> {
    val d = LocalDate.ofEpochDay(day)
    val codes = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val base = listOfNotNull(
        "Не повторять" to null,
        RepeatRule.parse("daily")?.let { "Каждый день" to it },
        RepeatRule.parse("weekly mon,tue,wed,thu,fri")?.let { "По будням" to it },
        RepeatRule.parse("weekly " + codes[d.dayOfWeek.value - 1])?.let { RepeatRule.describe(it).replaceFirstChar { c -> c.uppercase() } to it },
        RepeatRule.parse("monthly ${d.dayOfMonth}")?.let { RepeatRule.describe(it).replaceFirstChar { c -> c.uppercase() } to it },
        RepeatRule.parse("yearly %02d-%02d".format(d.monthValue, d.dayOfMonth))?.let { RepeatRule.describe(it).replaceFirstChar { c -> c.uppercase() } to it },
    )
    return if (current != null && base.none { it.second == current }) base + (RepeatRule.describe(current).replaceFirstChar { it.uppercase() } to current) else base
}

@Composable
internal fun EditorWindow(
    data: PinData,
    target: EditTarget,
    today: Long,
    onClose: () -> Unit,
    onSave: (Draft) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (EntryEntity, Boolean) -> Unit,
    onAttach: (String, String, ByteArray) -> Unit,
    onRemoveAttachment: (String, String) -> Unit,
    onCreateList: (String) -> Unit,
) {
    DialogWindow(
        onCloseRequest = onClose,
        title = if (target.id == null) "Новая задача" else "Задача",
        state = rememberDialogState(size = DpSize(620.dp, 780.dp)),
    ) {
        PinTheme {
            EditorContent(
                data, target, today, onClose, onSave, onDelete, onToggle, onAttach, onRemoveAttachment, onCreateList,
                pickFile = {
                    val dialog = FileDialog(window, "Приложить файл", FileDialog.LOAD).apply { isVisible = true }
                    dialog.file?.let { File(dialog.directory, it) }
                },
            )
        }
    }
}

/** Содержимое окна правки (отдельно от окна — для снимков экрана). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorContent(
    data: PinData,
    target: EditTarget,
    today: Long,
    onClose: () -> Unit,
    onSave: (Draft) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (EntryEntity, Boolean) -> Unit,
    onAttach: (String, String, ByteArray) -> Unit,
    onRemoveAttachment: (String, String) -> Unit,
    onCreateList: (String) -> Unit,
    pickFile: () -> File?,
) {
    val entry = target.id?.let(data.byId::get)
    val initial = remember(target) { entry?.let(Draft::of) ?: Draft(title = "", day = target.day) }
    var title by remember(target) { mutableStateOf(initial.title) }
    var dateText by remember(target) { mutableStateOf(DateText.format(initial.day)) }
    var timeText by remember(target) { mutableStateOf(initial.timeMinutes?.let(RuText::time) ?: "") }
    var priority by remember(target) { mutableStateOf(initial.priority) }
    var repeat by remember(target) { mutableStateOf(initial.repeat) }
    var listId by remember(target) { mutableStateOf(initial.listId) }
    var tags by remember(target) { mutableStateOf(initial.tags.joinToString(", ")) }
    var body by remember(target) { mutableStateOf(TextFieldValue(initial.body)) }
    var error by remember(target) { mutableStateOf<String?>(null) }

    val day = DateText.parse(dateText, today)
    val time = if (timeText.isBlank()) null else DateText.parseTime(timeText)
    val editable = entry == null || entry.entryType == EntryType.ONE_TIME || entry.entryType == EntryType.RECURRING

    fun save() {
        when {
            title.isBlank() -> error = "Напишите, что сделать"
            day == null -> error = "Дата — в виде 4.11.2026"
            timeText.isNotBlank() && time == null -> error = "Время — в виде 18:30"
            else -> onSave(
                initial.copy(
                    title = title,
                    body = body.text,
                    day = day,
                    timeMinutes = time,
                    priority = priority,
                    tags = tags.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
                    repeat = repeat,
                    listId = listId,
                ),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!editable) {
                Text(
                    "Дни рождения, праздники и планы пока меняются только на телефоне.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            OutlinedTextField(title, { title = it; error = null }, label = { Text("Что сделать") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Label("Когда")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    dateText, { dateText = it; error = null },
                    label = { Text("Дата") },
                    singleLine = true,
                    isError = day == null,
                    supportingText = { Text(day?.let { RuText.relativeDayWithDate(it, today) } ?: "например, 4.11.2026") },
                    modifier = Modifier.width(200.dp),
                )
                OutlinedTextField(
                    timeText, { timeText = it; error = null },
                    label = { Text("Время") },
                    singleLine = true,
                    isError = timeText.isNotBlank() && time == null,
                    supportingText = { Text("пусто — без времени") },
                    modifier = Modifier.width(150.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Сегодня" to today, "Завтра" to today + 1, "Через неделю" to today + 7).forEach { (label, d) ->
                    FilterChip(selected = day == d, onClick = { dateText = DateText.format(d) }, label = { Text(label) })
                }
            }

            Label("Повтор")
            Choice(
                current = repeatChoices(day ?: today, repeat).firstOrNull { it.second == repeat }?.first ?: "Не повторять",
                options = repeatChoices(day ?: today, repeat).map { it.first to { repeat = it.second } },
            )

            Label("Приоритет")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Priority.LOW to "Низкий", Priority.NORMAL to "Обычный", Priority.HIGH to "Важный").forEach { (p, label) ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(label) })
                }
            }

            Label("Список")
            val lists = data.lists.filter { !it.deleted }.sortedBy { it.sortOrder }
            var newList by remember(target) { mutableStateOf("") }
            // Только что созданный список выбирается сам, как только появится в данных.
            var pending by remember(target) { mutableStateOf<String?>(null) }
            pending?.let { name -> lists.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { listId = it.id; pending = null } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Choice(
                    current = lists.firstOrNull { it.id == listId }?.name ?: "Без списка",
                    options = listOf("Без списка" to { listId = null }) + lists.map { l -> l.name to { listId = l.id } },
                )
                OutlinedTextField(newList, { newList = it }, placeholder = { Text("Новый список") }, singleLine = true, modifier = Modifier.width(180.dp))
                TextButton(onClick = {
                    val name = newList.trim()
                    if (name.isNotEmpty()) {
                        onCreateList(name)
                        pending = name
                        newList = ""
                    }
                }) { Text("Создать") }
            }

            OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Label("Описание (markdown: «- [ ] пункт», **жирный**, ссылки)")
            OutlinedTextField(
                value = body,
                onValueChange = { new ->
                    val cont = Markdown.continueList(body.text, new.text, new.selection.start)
                    body = if (cont != null) TextFieldValue(cont.first, TextRange(cont.second)) else new
                },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            if (body.text.isNotBlank()) {
                Box(Modifier.padding(start = 4.dp)) { MarkdownText(body.text) }
            }

            Label("Вложения")
            if (entry == null) {
                Text("Файлы можно приложить после сохранения задачи.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Attachments.parse(entry.attachments).forEach { a ->
                        InputChip(
                            selected = false,
                            onClick = { openAttachment(data, a) },
                            label = { Text("📎 " + a.name) },
                        )
                        TextButton(onClick = { onRemoveAttachment(entry.id, a.id) }) { Text("убрать") }
                    }
                }
                OutlinedButton(onClick = {
                    val file = pickFile() ?: return@OutlinedButton
                    if (file.length() > Attachments.MAX_BYTES) {
                        error = "Файл больше 10 МБ"
                    } else {
                        onAttach(entry.id, file.name, file.readBytes())
                    }
                }) { Text("📎 Приложить файл") }
                Text(
                    "До 10 МБ. Файлы едут на телефон в Pin.zip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            entry?.let { e ->
                Text(
                    buildList {
                        e.originalDay?.let { add("Изначально на " + RuText.relativeDay(it, today)) }
                        if (e.rollovers > 0) add(RuText.rollovers(e.rollovers))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry != null) {
                TextButton(onClick = { onDelete(entry.id) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                val done = entry.entryStatus == TaskStatus.DONE || (entry.schedule != null && entry.completedDay == today)
                TextButton(onClick = { onToggle(entry, !done); onClose() }) { Text(if (done) "Вернуть в работу" else "Выполнено") }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(onClick = ::save, enabled = editable) { Text("Сохранить") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Кнопка с выпадающим списком вариантов. */
@Composable
private fun Choice(current: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$current  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, pick) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { pick(); open = false })
            }
        }
    }
}
