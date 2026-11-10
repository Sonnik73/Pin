package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Holidays
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText

/** Праздники: встроенные можно выключать, свои — добавлять, править и удалять. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(
    ui: PinUi,
    onSave: (id: String?, title: String, month: Int, day: Int) -> Unit,
    onEnabled: (id: String, enabled: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val holidays = ui.entries.values.filter { it.entryType == EntryType.HOLIDAY }
        .sortedWith(compareBy({ (it.rule as? RepeatRule.Yearly)?.month }, { (it.rule as? RepeatRule.Yearly)?.day }))
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Праздники") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = "" }) { Icon(Icons.Filled.Add, contentDescription = "Свой праздник") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "Праздники показываются в «Сегодня» и календаре, а за день до них приходит напоминание. " +
                        "Свои — например, годовщину свадьбы — добавьте кнопкой «+».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(holidays, key = { it.id }) { h ->
                val builtIn = Holidays.byEntryId(h.id) != null
                val rule = h.rule as? RepeatRule.Yearly
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = !builtIn) { editing = h.id }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.title)
                        Text(
                            (rule?.let { RuText.dayMonth(it.day, it.month) } ?: "") +
                                " · " + (Holidays.byEntryId(h.id)?.let { it.kind.emoji + " " + it.kind.title } ?: "свой"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = !h.disabled, onCheckedChange = { onEnabled(h.id, it) })
                }
            }
        }
    }

    val id = editing
    if (id != null) {
        HolidayDialog(
            holiday = if (id.isEmpty()) null else ui.entries[id],
            onSave = { title, month, day -> onSave(id.ifEmpty { null }, title, month, day); editing = null },
            onDelete = { onDelete(id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun HolidayDialog(holiday: EntryEntity?, onSave: (String, Int, Int) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val rule = holiday?.rule as? RepeatRule.Yearly
    var title by rememberSaveable { mutableStateOf(holiday?.title ?: "") }
    var date by rememberSaveable { mutableStateOf(rule?.let { dateDigits(RuText.formatDayMonth(it.day, it.month, null)) } ?: "") }
    val parsed = RuText.parseDayMonth(RuText.dateFromDigits(date))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (holiday == null) "Свой праздник" else "Праздник") },
        // Окно учитывает клавиатуру: сжимается над ней, а поле прокручивается к курсору.
        properties = KeyboardAwareDialog,
        modifier = Modifier.keyboardAware(),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Название") },
                    placeholder = { Text("Годовщина свадьбы") },
                    singleLine = true,
                    keyboardOptions = SENTENCES,
                )
                DateField(date, { date = it }, "Дата", withYear = false, isError = date.length == 4 && parsed == null)
                if (holiday != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && parsed != null, onClick = { parsed?.let { onSave(title, it.month, it.day) } }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
