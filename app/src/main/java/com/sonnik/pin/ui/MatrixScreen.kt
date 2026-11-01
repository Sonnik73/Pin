package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Matrix
import com.sonnik.pin.engine.MatrixItem
import com.sonnik.pin.engine.Quadrant
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskStatus

/** Открытые задачи и планы — то, что раскладывается по матрице. */
fun PinUi.matrixItems(): List<MatrixItem> = entries.values
    .filter { !it.disabled && it.entryStatus == TaskStatus.OPEN }
    .filter {
        when (it.entryType) {
            EntryType.ONE_TIME, EntryType.RECURRING -> it.dueDay != null
            EntryType.PLAN -> true
            else -> false
        }
    }
    .map { MatrixItem(it.id, it.entryType, it.dueDay, it.entryPriority, it.rollovers, it.timeMinutes) }

/** Матрица Эйзенхауэра: срочно — сегодня, завтра и перенесённое; важно — высокий приоритет. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(
    ui: PinUi,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onImportant: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val board = remember(ui) { Matrix.build(ui.matrixItems(), ui.today) }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Матрица") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Срочно — на сегодня, завтра и перенесённое. Важно — ★ (высокий приоритет). Звёздочка переносит задачу между столбцами.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            listOf(Quadrant.DO to Quadrant.SCHEDULE, Quadrant.QUICK to Quadrant.LATER).forEach { (left, right) ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuadrantCard(left, board.getValue(left), ui, onOpen, onToggle, onImportant, Modifier.weight(1f))
                    QuadrantCard(right, board.getValue(right), ui, onOpen, onToggle, onImportant, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun quadrantColors(q: Quadrant): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (q) {
        Quadrant.DO -> errorContainer to onErrorContainer
        Quadrant.SCHEDULE -> primaryContainer to onPrimaryContainer
        Quadrant.QUICK -> tertiaryContainer to onTertiaryContainer
        Quadrant.LATER -> surfaceContainerHigh to onSurfaceVariant
    }
}

@Composable
private fun QuadrantCard(
    q: Quadrant,
    items: List<MatrixItem>,
    ui: PinUi,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onImportant: (String, Boolean) -> Unit,
    modifier: Modifier,
) {
    val (bg, fg) = quadrantColors(q)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("${q.title} · ${items.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp))
            Text(q.hint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
            if (items.isEmpty()) {
                Text(
                    if (q == Quadrant.DO) "Пусто — хорошо." else "—",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    val entry = ui.entries[item.id] ?: return@items
                    Row(Modifier.fillMaxWidth().clickable { onOpen(item.id) }, verticalAlignment = Alignment.CenterVertically) {
                        if (item.type == EntryType.PLAN) {
                            Text("💡", modifier = Modifier.padding(horizontal = 12.dp))
                        } else {
                            Checkbox(checked = false, onCheckedChange = { onToggle(item.id, true) }, modifier = Modifier.size(40.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                item.day?.let { RuText.relativeDay(it, ui.today) },
                                item.timeMinutes?.let { RuText.time(it) },
                                entry.listId?.let { ui.listsById[it]?.name },
                            )
                            if (meta.isNotEmpty()) {
                                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val important = q.important
                        IconButton(onClick = { onImportant(item.id, !important) }, modifier = Modifier.size(36.dp)) {
                            Text(
                                if (important) "★" else "☆",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { contentDescription = if (important) "Сделать не важной" else "Сделать важной" },
                            )
                        }
                    }
                }
            }
        }
    }
}
