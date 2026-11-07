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

/** «Среда, 4 ноября». */
internal fun longDate(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    val weekday = d.dayOfWeek.getDisplayName(TextStyle.FULL, RU).replaceFirstChar { it.uppercase() }
    return "$weekday, ${RuText.dayMonth(d.dayOfMonth, d.monthValue)}"
}

private fun monthTitle(first: LocalDate): String =
    first.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() } + " " + first.year

// ---------- Сегодня ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayPane(data: PinData, today: Long) {
    val actions = LocalActions.current
    val board = remember(data, today) { data.board(today) }
    val events = remember(data, today) { data.upcomingEvents(today, 7) }
    val habits = remember(data, today) { data.habitsFor(today) }
    var listFilter by remember { mutableStateOf<String?>(null) }
    val lists = data.lists.filter { !it.deleted }.sortedBy { it.sortOrder }
    val active = listFilter?.takeIf { it in data.listsById }
    val inList: (List<TaskSnapshot>) -> List<TaskSnapshot> = { tasks -> if (active == null) tasks else tasks.filter { data.byId[it.id]?.listId == active } }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val toggleSection: (String) -> Unit = { t -> collapsed = if (t in collapsed) collapsed - t else collapsed + t }
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сегодня", style = MaterialTheme.typography.headlineLarge)
                        Text(longDate(today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { actions.add(today) }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Задача")
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (Feature.QUICK_ADD.on()) {
                    QuickAddField(today)
                    Spacer(Modifier.height(12.dp))
                }
                if (board.progress.total > 0) ProgressCard(board.progress.done, board.progress.total, board.progress.rolled, board.progress.important)
                if (Feature.LISTS.on() && lists.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.material3.FilterChip(selected = active == null, onClick = { listFilter = null }, label = { Text("Все") })
                        lists.forEach { l ->
                            androidx.compose.material3.FilterChip(
                                selected = active == l.id,
                                onClick = { listFilter = if (active == l.id) null else l.id },
                                label = { Text(l.name) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(listColor(l.color))) },
                            )
                        }
                    }
                }
                if (events.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    EventsCard(events, today)
                }
                if (Feature.REVIEW.on() && board.rolledOver.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { actions.review() },
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Разобрать перенесённые · ${board.rolledOver.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("По одной: сегодня, на дату, в планы или удалить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
        if (Feature.HABITS.on() && habits.isNotEmpty()) {
            item(key = "habits-header") {
                SectionTitle("Привычки · ${habits.count { today in it.second.doneDays }}/${habits.size}")
            }
            items(habits, key = { "habit:${it.first.id}" }) { (entry, info) ->
                Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)) {
                    HabitTodayRow(entry, today in info.doneDays, Habits.stats(info, today).current) { actions.toggleHabit(entry.id, today) }
                }
            }
        }
        section("Перенесённые", inList(board.rolledOver), data, today)
        section("На сегодня", inList(board.today), data, today)
        section("Выполнено", inList(board.done), data, today, collapsed = "Выполнено" in collapsed, onCollapse = toggleSection)
        item(key = "archive") {
            Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 16.dp)) {
                TextButton(onClick = { actions.search(com.sonnik.pin.engine.SearchState.DONE) }) { Text("Архив выполненных →") }
            }
        }
        if (Feature.UPCOMING.on()) {
            section("Скоро", inList(board.upcoming), data, today, showDay = true, collapsed = "Скоро" in collapsed, onCollapse = toggleSection)
        }
        if (board.openCount == 0 && board.done.isEmpty() && board.upcoming.isEmpty() && habits.isEmpty()) {
            item { Box(Modifier.height(240.dp)) { Centered("На сегодня задач нет") } }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: (() -> Unit)? = null) {
    Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 22.dp, bottom = 6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick) else Modifier,
        )
    }
}

/** Привычка на «Сегодня»: кружок отмечает сегодняшний день. */
@Composable
private fun HabitTodayRow(entry: EntryEntity, done: Boolean, streak: Int, onToggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (done) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(2.dp, if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { if (done) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.width(14.dp))
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (streak > 0) Text("🔥 $streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    tasks: List<TaskSnapshot>,
    data: PinData,
    today: Long,
    showDay: Boolean = false,
    collapsed: Boolean = false,
    onCollapse: ((String) -> Unit)? = null,
) {
    if (tasks.isEmpty()) return
    item(key = "h:$title") {
        SectionTitle(
            (if (onCollapse != null) (if (collapsed) "▸ " else "▾ ") else "") + "$title · ${tasks.size}",
            if (title == "Перенесённые") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onCollapse?.let { { it(title) } },
        )
    }
    if (collapsed) return
    items(tasks, key = { "$title:${it.id}" }) { task ->
        val entry = data.byId[task.id] ?: return@items
        Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)) {
            TaskCard(task, entry, data, today, showDay)
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, rolled: Int, important: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val share = if (total == 0) 0f else done.toFloat() / total
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { share },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 7.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
                Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(if (total == 0) "Свободный день" else "Сделано $done из $total", style = MaterialTheme.typography.titleLarge)
                val parts = buildList {
                    if (total > done) add("осталось ${total - done}")
                    if (rolled > 0) add("перенесено $rolled")
                    if (important > 0) add("$important важн" + if (important == 1) "ая" else "ых")
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EventsCard(events: List<CalendarEvent>, today: Long) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (ev in events.take(6)) {
                Text(
                    RuText.relativeDay(ev.day, today).replaceFirstChar { it.uppercase() } + " — " + eventText(ev),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** «🎂 Аня — исполнится 51» или «🎉 День народного единства». */
internal fun eventText(ev: CalendarEvent): String =
    if (ev.birthday) "🎂 ${ev.name}" + (ev.age?.let { " — исполнится $it" } ?: "") else "${ev.emoji} ${ev.name}"

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
private fun taskMenu(entry: EntryEntity, done: Boolean, today: Long, actions: Actions): List<Pair<String, () -> Unit>> {
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
private fun MoreMenu(items: List<Pair<String, () -> Unit>>) {
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
private fun StatusMark(done: Boolean, onClick: (() -> Unit)?) {
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
private fun Chip(text: String, onClick: () -> Unit) {
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

// ---------- Описание в markdown ----------

@Composable
internal fun MarkdownText(text: String, onToggleTask: ((Int) -> Unit)? = null) {
    val blocks = remember(text) { Markdown.parse(text) }
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (b in blocks) {
            when (b) {
                is Markdown.Block.Heading -> Text(inlines(b.inlines, link), style = if (b.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
                is Markdown.Block.Paragraph -> Text(inlines(b.inlines, link), style = MaterialTheme.typography.bodyMedium)
                is Markdown.Block.Item -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(b.marker + " ", style = MaterialTheme.typography.bodyMedium)
                    Text(inlines(b.inlines, link), style = MaterialTheme.typography.bodyMedium)
                }
                is Markdown.Block.Task -> Row(
                    Modifier.padding(start = (b.indent * 16).dp)
                        .then(if (onToggleTask != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { onToggleTask(b.line) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (b.checked) "☑ " else "☐ ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        inlines(b.inlines, link),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (b.checked) TextDecoration.LineThrough else null,
                        color = if (b.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                is Markdown.Block.Quote -> Text(
                    inlines(b.inlines, link),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                is Markdown.Block.Code -> Text(
                    b.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                )
                Markdown.Block.Rule -> HorizontalDivider()
            }
        }
    }
}

private fun inlines(list: List<Markdown.Inline>, link: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    for (i in list) {
        when (i) {
            is Markdown.Inline.Plain -> append(i.text)
            is Markdown.Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(i.text) }
            is Markdown.Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(i.text) }
            is Markdown.Inline.BoldItalic -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(i.text) }
            is Markdown.Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(i.text) }
            is Markdown.Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(i.text) }
            is Markdown.Inline.Link -> withLink(LinkAnnotation.Url(i.url, link)) { append(i.text) }
        }
    }
}

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
private fun DayCell(
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
private fun DayDetails(data: PinData, day: Long, today: Long, events: List<CalendarEvent>, modifier: Modifier) {
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

/** Быстрое добавление строкой: «завтра в 18 позвонить маме !», «12 ноября отчёт #работа @дом». */
@Composable
private fun QuickAddField(today: Long) {
    val actions = LocalActions.current
    var text by remember { mutableStateOf("") }
    val draft = remember(text, today) { if (text.isBlank()) null else com.sonnik.pin.engine.QuickAdd.parse(text, today) }
    fun submit() {
        val d = draft ?: return
        if (d.title.isBlank()) return
        actions.quickAdd(d)
        text = ""
    }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.replace("\n", "") },
            placeholder = { Text("Быстро: «завтра в 18 позвонить» — Enter добавит") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { submit(); true } else false
            },
            shape = RoundedCornerShape(14.dp),
        )
        draft?.takeIf { it.title.isNotBlank() }?.let { d ->
            Text(
                "«${d.title}» — " + com.sonnik.pin.engine.QuickAdd.describe(d, today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
        }
    }
}
