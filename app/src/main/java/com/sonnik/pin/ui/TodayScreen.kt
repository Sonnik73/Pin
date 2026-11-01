package com.sonnik.pin.ui

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
        val activeFilter = listFilter?.takeIf { it in ui.listsById }
        val inList: (List<TaskSnapshot>) -> List<TaskSnapshot> = { tasks ->
            if (activeFilter == null) tasks else tasks.filter { ui.entries[it.id]?.listId == activeFilter }
        }
        val board = ui.board
        val empty = board.openCount == 0 && board.done.isEmpty() && board.upcoming.isEmpty() && ui.events.isEmpty() && ui.habits.isEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "quick") {
                QuickAddField(ui.today, onQuickAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (ui.lists.isNotEmpty()) {
                item(key = "lists") {
                    ListFilterRow(ui.lists, activeFilter, { listFilter = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }
            if (empty) {
                item(key = "empty") { EmptyToday(Modifier.height(360.dp)) }
            }
            if (focus != null) {
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
            if (board.rolledOver.isNotEmpty()) {
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
            val todayHabits = ui.habits.filter { Habits.isScheduled(it, ui.today) || ui.today in it.doneDays }
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
            section("Перенесённые", inList(board.rolledOver), ui, onOpen, onToggle, accent = true)
            section("На сегодня", inList(board.today), ui, onOpen, onToggle)
            if (board.openCount == 0 && board.done.isNotEmpty()) {
                item {
                    Text(
                        "Всё сделано. Свободный вечер!",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            section("Выполнено", inList(board.done), ui, onOpen, onToggle)
            section("Скоро", inList(board.upcoming), ui, onOpen, onToggle, showDay = true)
        }
    }

}

private fun LazyListScope.section(
    title: String,
    tasks: List<TaskSnapshot>,
    ui: PinUi,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    accent: Boolean = false,
    showDay: Boolean = false,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$title") {
        Text(
            "$title · ${tasks.size}",
            style = MaterialTheme.typography.titleSmall,
            color = if (accent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(tasks, key = { "$title-${it.id}" }) { task ->
        val entry = ui.entries[task.id] ?: return@items
        TaskRow(task, entry, ui.today, showDay, entry.listId?.let { ui.listsById[it] }, onOpen = { onOpen(task.id) }, onToggle = { onToggle(task.id, it) })
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
) {
    val done = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = onToggle)
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
                Markdown.checklist(entry.body).takeIf { it.second > 0 }?.let { (d, t) -> add("☑ $d/$t" to Color.Unspecified) }
                entry.tagList.forEach { add("#$it" to Color.Unspecified) }
            }
            if (meta.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    meta.forEach { (text, color) ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                            maxLines = 1,
                        )
                    }
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
            Text("📌", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "На сегодня ничего не приколото.\nНажмите «Задача», чтобы добавить первую.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
