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

internal fun monthTitle(first: LocalDate): String =
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
internal fun SectionTitle(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: (() -> Unit)? = null) {
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
internal fun HabitTodayRow(entry: EntryEntity, done: Boolean, streak: Int, onToggle: () -> Unit) {
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

internal fun androidx.compose.foundation.lazy.LazyListScope.section(
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
internal fun ProgressCard(done: Int, total: Int, rolled: Int, important: Int) {
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
internal fun EventsCard(events: List<CalendarEvent>, today: Long) {
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

/** Быстрое добавление строкой: «завтра в 18 позвонить маме !», «12 ноября отчёт #работа @дом».
 *  Как на телефоне (0.89): распознанное подсвечивается, атрибуты — чипами; поправка — выпадающим списком. */
@Composable
internal fun QuickAddField(today: Long) {
    val actions = LocalActions.current
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, today) { if (text.isBlank()) null else com.sonnik.pin.engine.QuickAdd.parse(text, today) }
    // Правки чипами живут, пока текст не поправили: изменил строку — разбор заново.
    var dayOverride by remember(text, today) { mutableStateOf<Long?>(null) }
    var timeOverride by remember(text, today) { mutableStateOf<Int?>(null) }
    var timeSet by remember(text, today) { mutableStateOf(false) }
    var priorityOverride by remember(text, today) { mutableStateOf<com.sonnik.pin.engine.Priority?>(null) }
    val draft = parsed?.let { p ->
        p.copy(
            day = dayOverride ?: p.day,
            timeMinutes = if (timeSet) timeOverride else p.timeMinutes,
            priority = priorityOverride ?: p.priority,
        )
    }
    fun submit() {
        val d = draft ?: return
        if (d.title.isBlank()) return
        actions.quickAdd(d)
        text = ""
    }
    // Подсветка распознанного: дата/время/«!» — акцентом, теги и список — вторым акцентом.
    val colors = MaterialTheme.colorScheme
    val highlight = remember(parsed, colors.primary, colors.tertiary) {
        parsed?.let { p ->
            object : androidx.compose.ui.text.input.VisualTransformation {
                private val marks = com.sonnik.pin.engine.QuickAdd.marks(text, p)
                override fun filter(text: AnnotatedString) = androidx.compose.ui.text.input.TransformedText(
                    buildAnnotatedString {
                        append(text.text)
                        marks.forEach { m ->
                            val style = when (m.kind) {
                                com.sonnik.pin.engine.QuickAdd.QuickMarkKind.TAG, com.sonnik.pin.engine.QuickAdd.QuickMarkKind.LIST ->
                                    SpanStyle(color = colors.tertiary)
                                else -> SpanStyle(color = colors.primary)
                            }
                            addStyle(style, m.start, m.end.coerceAtMost(text.length))
                        }
                    },
                    androidx.compose.ui.text.input.OffsetMapping.Identity,
                )
            }
        } ?: androidx.compose.ui.text.input.VisualTransformation.None
    }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.replace("\n", "") },
            placeholder = { Text("Быстро: «завтра в 18 позвонить» — Enter добавит") },
            singleLine = true,
            visualTransformation = highlight,
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { submit(); true } else false
            },
            shape = RoundedCornerShape(14.dp),
        )
        when {
            draft == null -> Row(Modifier.padding(start = 6.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("завтра в 9 тренировка", "в пятницу отчёт #работа", "! срочно позвонить").forEach { ex ->
                    QuickChip(ex, onClick = { text = ex })
                }
            }
            draft.title.isBlank() -> Text(
                "Добавьте, что сделать",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
            else -> Row(Modifier.padding(start = 6.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickChoice(
                    com.sonnik.pin.engine.RuText.relativeDay(draft.day, today),
                    listOf(
                        "сегодня" to { dayOverride = today },
                        "завтра" to { dayOverride = today + 1 },
                        "послезавтра" to { dayOverride = today + 2 },
                        "через неделю" to { dayOverride = today + 7 },
                        "← на день раньше" to { dayOverride = draft.day - 1 },
                        "на день позже →" to { dayOverride = draft.day + 1 },
                    ),
                )
                val time = draft.timeMinutes
                QuickChoice(
                    time?.let { com.sonnik.pin.engine.RuText.time(it) } ?: "+ время",
                    listOf(
                        "9:00" to { timeOverride = 9 * 60; timeSet = true },
                        "12:00" to { timeOverride = 12 * 60; timeSet = true },
                        "18:00" to { timeOverride = 18 * 60; timeSet = true },
                        "−1 ч" to { timeOverride = ((time ?: 9 * 60) - 60).coerceAtLeast(0); timeSet = true },
                        "+30 мин" to { timeOverride = ((time ?: 9 * 60) + 30).coerceAtMost(23 * 60 + 59); timeSet = true },
                        "без времени" to { timeOverride = null; timeSet = true },
                    ),
                )
                if (draft.priority == com.sonnik.pin.engine.Priority.HIGH) {
                    QuickChip("важная", active = true, onClick = { priorityOverride = parsed.priority })
                } else {
                    QuickChip("+ важно", onClick = { priorityOverride = com.sonnik.pin.engine.Priority.HIGH })
                }
                draft.tags.forEach { QuickChip("#$it") }
                draft.list?.let { QuickChip("@$it") }
            }
        }
    }
}

/** Чип быстрого ввода: просто подпись или кнопка (поправить атрибут, подставить пример). */
@Composable
private fun QuickChip(label: String, active: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (onClick == null) MaterialTheme.colorScheme.onSurfaceVariant
            else if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Чип-атрибут с выпадающими вариантами поправки — даты и времени. */
@Composable
private fun QuickChoice(current: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuickChip("$current  ▾", active = open, onClick = { open = true })
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, pick) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { pick(); open = false })
            }
        }
    }
}
