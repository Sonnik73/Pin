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

// ---------- Строка задачи ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskCard(task: TaskSnapshot, entry: EntryEntity, data: PinData, today: Long, showDay: Boolean = false, canToggle: Boolean = true) {
    val actions = LocalActions.current
    val done = task.status == TaskStatus.DONE
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val list = entry.listId?.let(data.listsById::get)
    val stripe = list?.let { listColor(it.color) } ?: Color.Transparent
    val menu = taskMenu(entry, done, today, actions)
    androidx.compose.foundation.ContextMenuArea(items = { menu.map { (label, act) -> androidx.compose.foundation.ContextMenuItem(label, act) } }) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { actions.open(entry) },
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusMark(done, if (canToggle) ({ actions.toggle(entry, !done) }) else null)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        data.titleOf(entry),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (menu.isNotEmpty()) MoreMenu(menu)
                }
                val meta = buildList<Pair<String, Color?>> {
                    list?.let { add("● " + it.name to listColor(it.color)) }
                    if (showDay) add(RuText.relativeDayWithDate(task.currentDay, today) to null)
                    task.timeMinutes?.let { add(RuText.time(it) to null) }
                    if (task.priority == Priority.HIGH && !done) add("важно" to MaterialTheme.colorScheme.primary)
                    if (task.rollovers > 0) add(RuText.rollovers(task.rollovers) to MaterialTheme.colorScheme.tertiary)
                    if (entry.entryType == EntryType.RECURRING) entry.rule?.let { add("↻ " + RepeatRule.describe(it) to null) }
                    entry.tagList.forEach { add("#$it" to null) }
                }
                val attachments = Attachments.parse(entry.attachments)
                if (meta.isNotEmpty() || entry.body.isNotBlank() || attachments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        Modifier.padding(start = 34.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        meta.forEach { (text, color) ->
                            Text(text, style = MaterialTheme.typography.labelLarge, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (entry.body.isNotBlank()) {
                            val (checked, total) = Markdown.checklist(entry.body)
                            Chip((if (expanded) "▾ " else "▸ ") + if (total > 0) "пункты $checked/$total" else "описание") { expanded = !expanded }
                        }
                        attachments.forEach { a -> Chip("📎 " + a.name) { openAttachment(data, a) } }
                    }
                }
                if (expanded && entry.body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.padding(start = 34.dp)) {
                        MarkdownText(entry.body, onToggleTask = { line -> actions.setBody(entry, Markdown.toggleTask(entry.body, line)) })
                    }
                }
            }
        }
    }
    }
}

/** Действия с задачей: правый щелчок по карточке или кнопка «⋯». */
internal fun taskMenu(entry: EntryEntity, done: Boolean, today: Long, actions: Actions): List<Pair<String, () -> Unit>> {
    if (entry.entryType != EntryType.ONE_TIME && entry.entryType != EntryType.RECURRING) return emptyList()
    return buildList {
        add("Открыть" to { actions.open(entry) })
        add((if (done) "Вернуть в работу" else "Выполнено") to { actions.toggle(entry, !done) })
        if (!done) {
            add("На сегодня" to { actions.reschedule(entry, today) })
            add("На завтра" to { actions.reschedule(entry, today + 1) })
            add("Через неделю" to { actions.reschedule(entry, today + 7) })
            if (Feature.FOCUS.on()) add("⏱ Фокус по задаче" to { actions.focus(entry) })
            if (entry.schedule != null) add("Пропустить этот повтор" to { actions.skip(entry) })
            else add("В планы «когда-нибудь»" to { actions.toPlans(entry) })
        }
        add("Удалить" to { actions.delete(entry) })
    }
}

@Composable
internal fun MoreMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "⋯",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).clickable { open = true }.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, act) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { open = false; act() })
            }
        }
    }
}

@Composable
internal fun StatusMark(done: Boolean, onClick: (() -> Unit)?) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (done) Modifier.background(color) else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp))),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** Цвет списка: в тёмной теме — светлый вариант, как на телефоне. */
@Composable
internal fun listColor(name: String): Color =
    ListColor.parse(name).let { Color(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) it.dark else it.light) }

/** Открыть вложение программой Windows по умолчанию: файл копируется во временную папку. */
internal fun openAttachment(data: PinData, a: Attachment) {
    val bytes = data.blob(a) ?: return
    val dir = File(System.getProperty("java.io.tmpdir"), "Pin/attachments/${a.id}")
    dir.mkdirs()
    val file = File(dir, a.name)
    if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    runCatching { Desktop.getDesktop().open(file) }
}
