package com.sonnik.pin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RuText

/** Быстрая правка задачи — панель снизу: название, день, время, важность. «Развернуть» — полный редактор. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEditSheet(
    entry: EntryEntity,
    today: Long,
    onSave: (TaskDraft) -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        QuickEditContent(entry, today, onSave = { onSave(it); onDismiss() }, onExpand = onExpand)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickEditContent(entry: EntryEntity, today: Long, onSave: (TaskDraft) -> Unit, onExpand: () -> Unit) {
    var title by rememberSaveable(entry.id) { mutableStateOf(entry.title) }
    var day by rememberSaveable(entry.id) { mutableStateOf(entry.dueDay ?: today) }
    var time by rememberSaveable(entry.id) { mutableStateOf(entry.timeMinutes) }
    var important by rememberSaveable(entry.id) { mutableStateOf(entry.entryPriority == Priority.HIGH) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    fun save() {
        if (title.isBlank()) return
        onSave(
            TaskDraft(
                title = title.trim(),
                body = entry.body,
                day = day,
                timeMinutes = time,
                // Важность переключается, «низкий» сохраняется, если его не трогали.
                priority = if (important) Priority.HIGH else entry.entryPriority.takeIf { it != Priority.HIGH } ?: Priority.NORMAL,
                tags = entry.tagList,
                remindBefore = if (time == null) 0 else entry.remindBefore,
                listId = entry.listId,
            ),
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Что сделать") },
            keyboardOptions = SENTENCES,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(today to "Сегодня", today + 1 to "Завтра", today + 7 to "Через неделю").forEach { (d, label) ->
                FilterChip(selected = day == d, onClick = { day = d }, label = { Text(label) })
            }
            if (day !in listOf(today, today + 1, today + 7)) {
                FilterChip(selected = true, onClick = {}, label = { Text(RuText.relativeDayWithDate(day, today)) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = time != null, onClick = { pickTime = true }, label = { Text(time?.let { "🕑 " + RuText.time(it) } ?: "🕑 Без времени") })
            FilterChip(selected = important, onClick = { important = !important }, label = { Text(if (important) "❗ Важная" else "Важная") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExpand) { Text("Развернуть ↗") }
            Text("", Modifier.weight(1f))
            Button(onClick = ::save, enabled = title.isNotBlank()) { Text("Сохранить") }
        }
    }
    if (pickTime) {
        val initial = time ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Время") },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { time = state.hour * 60 + state.minute; pickTime = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { time = null; pickTime = false }) { Text("Без времени") } },
        )
    }
}
