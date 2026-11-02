package com.sonnik.pin.ui

import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.offset
import com.sonnik.pin.data.ListEntity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.FocusState
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.Markdown
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.QuickDraft
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    ui: PinUi,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSettings: () -> Unit,
    onQuickAdd: (QuickDraft) -> Unit = {},
    onSearch: () -> Unit = {},
    onReview: () -> Unit = {},
    onHabitToggle: (String) -> Unit = {},
    onHabitOpen: (String) -> Unit = {},
    focus: FocusState? = null,
    onFocus: () -> Unit = {},
    onMatrix: () -> Unit = {},
    onArchive: () -> Unit = {},
    onTomorrow: (String) -> Unit = {},
    onBodyChange: (String, String) -> Unit = { _, _ -> },
    /** Какие задачи раскрыты сразу (для скриншотов). */
    expandedIds: Set<String> = emptySet(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Сегодня", fontWeight = FontWeight.SemiBold)
                        Text(
                            RuText.headline(ui.today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (com.sonnik.pin.engine.Feature.MATRIX.on()) IconButton(onClick = onMatrix) {
                        Icon(PinIcons.Matrix, contentDescription = "Матрица Эйзенхауэра")
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск и все записи")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Задача") },
            )
        },
    ) { padding ->
        var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
        // Свёрнутые разделы («Выполнено», «Скоро») — список названий.
        var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
        val toggleSection: (String) -> Unit = { t -> collapsed = if (t in collapsed) collapsed - t else collapsed + t }
        val listsOn = com.sonnik.pin.engine.Feature.LISTS.on()
        val habitsOn = com.sonnik.pin.engine.Feature.HABITS.on()
        val reviewOn = com.sonnik.pin.engine.Feature.REVIEW.on()
        val upcomingOn = com.sonnik.pin.engine.Feature.UPCOMING.on()
        val quickOn = com.sonnik.pin.engine.Feature.QUICK_ADD.on()
        val focusOn = com.sonnik.pin.engine.Feature.FOCUS.on()
        val activeFilter = listFilter?.takeIf { listsOn && it in ui.listsById }
        val inList: (List<TaskSnapshot>) -> List<TaskSnapshot> = { tasks ->
            if (activeFilter == null) tasks else tasks.filter { ui.entries[it.id]?.listId == activeFilter }
        }
        val board = ui.board
        val empty = board.openCount == 0 && board.done.isEmpty() && board.upcoming.isEmpty() && ui.events.isEmpty() && ui.habits.isEmpty()
        // Конфетти, когда дела дня закрыты (переход «ещё есть» → «всё сделано»).
        var confetti by remember { mutableIntStateOf(0) }
        var wasAllDone by remember { mutableStateOf(board.progress.allDone) }
        LaunchedEffect(board.progress.allDone) {
            if (board.progress.allDone && !wasAllDone) confetti++
            wasAllDone = board.progress.allDone
        }
        Box(Modifier.fillMaxSize()) {
        Confetti(confetti, Modifier.fillMaxSize().zIndex(1f))
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (board.progress.total > 0) {
                item(key = "progress") {
                    ProgressHeader(board.progress, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp))
                }
            }
            if (quickOn) item(key = "quick") {
                QuickAddField(ui.today, onQuickAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (listsOn && ui.lists.isNotEmpty()) {
                item(key = "lists") {
                    ListFilterRow(ui.lists, activeFilter, { listFilter = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }
            if (empty) {
                item(key = "empty") { EmptyToday(Modifier.height(360.dp)) }
            }
            if (focus != null && focusOn) {
                item(key = "focus") {
                    FocusBanner(
                        focus,
                        focus.taskId?.let { ui.entries[it]?.title },
                        onClick = onFocus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (ui.events.isNotEmpty()) {
                item(key = "events") { EventsCard(ui.events) }
            }
            if (reviewOn && board.rolledOver.isNotEmpty()) {
                item(key = "review") {
                    Surface(
                        onClick = onReview,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Разобрать перенесённые · ${board.rolledOver.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "По одной: сегодня, на дату, в планы или удалить",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            // Привычки на сегодня (и отмеченные сегодня вне расписания).
            val todayHabits = if (!habitsOn) emptyList() else ui.habits.filter { Habits.isScheduled(it, ui.today) || ui.today in it.doneDays }
            if (todayHabits.isNotEmpty()) {
                item(key = "habits-header") {
                    Text(
                        "Привычки · ${todayHabits.count { ui.today in it.doneDays }}/${todayHabits.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(todayHabits, key = { "habit-${it.id}" }) { h ->
                    HabitRow(ui.entries[h.id]?.title ?: "", h, ui.today, onToggle = { onHabitToggle(h.id) }, onOpen = { onHabitOpen(h.id) })
                }
            }
            section("Перенесённые", inList(board.rolledOver), ui, onOpen, onToggle, onBodyChange, expandedIds, accent = true, onTomorrow = onTomorrow)
            section("На сегодня", inList(board.today), ui, onOpen, onToggle, onBodyChange, expandedIds, onTomorrow = onTomorrow)
            section("Выполнено", inList(board.done), ui, onOpen, onToggle, onBodyChange, expandedIds, collapsible = true, collapsed = "Выполнено" in collapsed, onCollapse = toggleSection)
            // Выполненное видно здесь до конца дня; с завтрашнего — в архиве.
            item(key = "archive") {
                TextButton(onClick = onArchive, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Архив выполненных →")
                }
            }
            if (upcomingOn) section("Скоро", inList(board.upcoming), ui, onOpen, onToggle, onBodyChange, expandedIds, showDay = true, collapsible = true, collapsed = "Скоро" in collapsed, onCollapse = toggleSection)
        }
        }
    }

}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LazyListScope.section(
    title: String,
    tasks: List<TaskSnapshot>,
    ui: PinUi,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBodyChange: (String, String) -> Unit,
    expandedIds: Set<String>,
    accent: Boolean = false,
    showDay: Boolean = false,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onCollapse: (String) -> Unit = {},
    onTomorrow: (String) -> Unit = {},
) {
    if (tasks.isEmpty()) return
    // Заголовок прилипает к верху, пока листаешь его раздел.
    stickyHeader(key = "header-$title") {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            SectionHeader(title, tasks.size, accent, collapsible, collapsed, onToggle = { onCollapse(title) })
        }
    }
    if (collapsed) return
    items(tasks, key = { "$title-${it.id}" }) { task ->
        val entry = ui.entries[task.id] ?: return@items
        val done = task.status == TaskStatus.DONE
        // Плавный переезд между разделами и свайпы: вправо — готово, влево — на завтра.
        Box(Modifier.animateItem()) {
            SwipeTask(
                done = done,
                onDone = if (com.sonnik.pin.engine.Feature.SWIPE_DONE.on()) ({ onToggle(task.id, it) }) else null,
                onTomorrow = if (done || task.currentDay > ui.today) null else ({ onTomorrow(task.id) }),
            ) {
                TaskRow(task, entry, ui.today, showDay, entry.listId?.takeIf { com.sonnik.pin.engine.Feature.LISTS !in LocalDisabledFeatures.current }?.let { ui.listsById[it] }, onOpen = { onOpen(task.id) }, onToggle = { onToggle(task.id, it) }, onBody = { onBodyChange(task.id, it) }, startExpanded = task.id in expandedIds)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: TaskSnapshot,
    entry: EntryEntity,
    today: Long,
    showDay: Boolean,
    list: ListEntity?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onBody: (String) -> Unit,
    startExpanded: Boolean = false,
) {
    val realDone = task.status == TaskStatus.DONE
    // Отметка: галочка и зачёркивание сразу, а в «Выполнено» задача уезжает через мгновение.
    var completing by remember(task.id, realDone) { mutableStateOf(false) }
    LaunchedEffect(completing) {
        if (completing) {
            kotlinx.coroutines.delay(450)
            onToggle(true)
        }
    }
    val done = realDone || completing
    val fade by androidx.compose.animation.core.animateFloatAsState(if (completing) 0.55f else 1f, label = "fade")
    var expanded by rememberSaveable(task.id) { mutableStateOf(startExpanded) }
    // Карточка: полоска слева — цвет списка, а без списка — важность.
    val stripe = when {
        list != null -> list.color()
        task.priority == Priority.HIGH && !done -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Surface(
        color = if (done) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).alpha(fade),
    ) {
    Row(Modifier.height(IntrinsicSize.Min)) {
    Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
    Column(Modifier.weight(1f)) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { checked -> if (checked && !realDone) completing = true else onToggle(checked) })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = buildList {
                list?.let { add("● " + it.name to it.color()) }
                if (showDay) add(RuText.relativeDayWithDate(task.currentDay, today) to Color.Unspecified)
                task.timeMinutes?.let { add(RuText.time(it) to Color.Unspecified) }
                if (task.priority == Priority.HIGH && !done) add("важно" to MaterialTheme.colorScheme.primary)
                if (task.rollovers > 0) add(RuText.rollovers(task.rollovers) to MaterialTheme.colorScheme.tertiary)
                if (entry.entryType == EntryType.RECURRING) entry.rule?.let { add("↻ " + RepeatRule.describe(it) to Color.Unspecified) }
                entry.tagList.forEach { add("#$it" to Color.Unspecified) }
            }
            if (meta.isNotEmpty() || entry.body.isNotBlank() || entry.attachments != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    meta.forEach { (text, color) ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                            maxLines = 1,
                        )
                    }
                    BodyToggle(entry.body, expanded, { expanded = it }, Modifier)
                    AttachmentButton(com.sonnik.pin.engine.Attachments.parse(entry.attachments), LocalOpenAttachment.current)
                }
            }
        }
    }
    if (expanded && entry.body.isNotBlank()) {
        ExpandedBody(entry.body, onBody, Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp, bottom = 8.dp))
    }
    }
    }
    }
}

@Composable
private fun EventsCard(events: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            events.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
}

@Composable
private fun EmptyToday(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyIllustration(EmptyArt.TODAY)
            Spacer(Modifier.height(12.dp))
            Text(
                "На сегодня ничего не приколото.\nНажмите «Задача», чтобы добавить первую.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
