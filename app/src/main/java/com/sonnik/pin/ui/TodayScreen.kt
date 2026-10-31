package com.sonnik.pin.ui

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    ui: TodayUi,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDayBoundary: (Int) -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { settingsOpen = true }) {
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
        val board = ui.board
        if (board.openCount == 0 && board.done.isEmpty() && board.upcoming.isEmpty()) {
            EmptyToday(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            section("Перенесённые", board.rolledOver, ui, onOpen, onToggle, accent = true)
            section("На сегодня", board.today, ui, onOpen, onToggle)
            if (board.openCount == 0 && board.done.isNotEmpty()) {
                item {
                    Text(
                        "Всё сделано. Свободный вечер!",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            section("Выполнено", board.done, ui, onOpen, onToggle)
            section("Скоро", board.upcoming, ui, onOpen, onToggle, showDay = true)
        }
    }

    if (settingsOpen) {
        DayBoundaryDialog(
            current = ui.dayBoundaryMinutes,
            onPick = { onDayBoundary(it); settingsOpen = false },
            onDismiss = { settingsOpen = false },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    tasks: List<TaskSnapshot>,
    ui: TodayUi,
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
        TaskRow(task, entry, ui.today, showDay, onOpen = { onOpen(task.id) }, onToggle = { onToggle(task.id, it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: TaskSnapshot,
    entry: EntryEntity,
    today: Long,
    showDay: Boolean,
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
                if (showDay) add(RuText.relativeDay(task.currentDay, today) to Color.Unspecified)
                task.timeMinutes?.let { add(RuText.time(it) to Color.Unspecified) }
                if (task.priority == Priority.HIGH && !done) add("важно" to MaterialTheme.colorScheme.primary)
                if (task.rollovers > 0) add(RuText.rollovers(task.rollovers) to MaterialTheme.colorScheme.tertiary)
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

@Composable
private fun DayBoundaryDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = (0..6).map { it * 60 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Граница дня") },
        text = {
            Column {
                Text(
                    "Во сколько начинается новый день. Невыполненное переезжает на завтра в это время, " +
                        "а отмеченное ночью до границы засчитывается вчерашнему дню.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = minutes == current, onClick = { onPick(minutes) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = minutes == current, onClick = { onPick(minutes) })
                        Text(RuText.time(minutes) + if (minutes == 4 * 60) " — как в Крепости" else "")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
