package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.Markdown
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText

/** Что сделать с перенесённой задачей. */
sealed interface ReviewAction {
    data class Keep(val day: Long) : ReviewAction
    data object Done : ReviewAction
    data object ToPlans : ReviewAction
    data object SkipOccurrence : ReviewAction
    data object Delete : ReviewAction
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * «Разбор дня»: перенесённые задачи по одной. Для каждой — оставить на сегодня, перенести на дату, сделано,
 * в планы (или пропустить повтор у периодической), удалить. Очередь фиксируется при открытии.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(ui: PinUi, onAction: (String, ReviewAction) -> Unit, onClose: () -> Unit) {
    val queue = rememberSaveable { ArrayList(ui.board.rolledOver.map { it.id }) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    // Пропускаем то, что уже не нужно разбирать (удалено в другом месте).
    val position = (index until queue.size).firstOrNull { ui.entries[queue[it]] != null } ?: queue.size
    val id = queue.getOrNull(position)
    val entry = id?.let { ui.entries[it] }

    fun act(action: ReviewAction) {
        if (id != null) onAction(id, action)
        index = position + 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разбор перенесённых") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            if (queue.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { position.toFloat() / queue.size },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    if (entry != null) "${position + 1} из ${queue.size}" else "Разобрано: ${queue.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📌", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (queue.isEmpty()) "Перенесённых задач нет." else "Готово! Хвосты разобраны.",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onClose) { Text("К задачам") }
                    }
                }
                return@Column
            }
            val recurring = entry.rule != null
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 160.dp),
            ) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    val meta = buildList {
                        add(RuText.rollovers(entry.rollovers))
                        entry.originalDay?.let { add("изначально на ${RuText.relativeDayWithDate(it, ui.today)}") }
                        entry.rule?.let { add("↻ " + RepeatRule.describe(it)) }
                        entry.timeMinutes?.let { add("в " + RuText.time(it)) }
                    }
                    Text(meta.joinToString(" · "), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                    if (entry.tagList.isNotEmpty()) {
                        Text(entry.tagList.joinToString(" ") { "#$it" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.body.isNotBlank()) MarkdownView(entry.body)
                    Markdown.checklist(entry.body).takeIf { it.second > 0 }?.let { (d, t) ->
                        Text("Подпункты: $d из $t", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Button(onClick = { act(ReviewAction.Keep(ui.today)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Сделаю сегодня")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = { act(ReviewAction.Keep(ui.today + 1)) }, modifier = Modifier.weight(1f)) { Text("Завтра") }
                FilledTonalButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text("На дату…") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { act(ReviewAction.Done) }, modifier = Modifier.weight(1f)) { Text("Уже сделано") }
                if (recurring) {
                    OutlinedButton(onClick = { act(ReviewAction.SkipOccurrence) }, modifier = Modifier.weight(1f)) { Text("Пропустить") }
                } else {
                    OutlinedButton(onClick = { act(ReviewAction.ToPlans) }, modifier = Modifier.weight(1f)) { Text("В планы") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { act(ReviewAction.Delete) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { index = position + 1 }) { Text("Не сейчас →") }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (ui.today + 2) * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { act(ReviewAction.Keep(Math.floorDiv(it, DAY_MS))) }
                    pickDate = false
                }) { Text("Перенести") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}
