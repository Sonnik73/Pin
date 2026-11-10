package com.sonnik.pin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Moments
import com.sonnik.pin.engine.Search
import java.time.LocalDate

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Важные даты по годам, новые сверху; поиск по названию, заметке и тегам. Под каждой — счётчик «прошло…». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    ui: PinUi,
    onSave: (id: String?, title: String, body: String, day: Long, end: Long?, tags: List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    /** Переключатель «Планы / Важные даты» вместо заголовка. */
    switcher: (@Composable () -> Unit)? = null,
) {
    // null — диалог закрыт; "" — новая дата.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(ui.entries) { ui.entries.values.filter { it.entryType == EntryType.MOMENT && it.momentDay != null } }
    val found = all.filter { Search.matchesAll(query, listOf(it.title, it.body, it.tagList.joinToString(" "))) }
        .sortedWith(compareByDescending<EntryEntity> { it.momentDay }.thenByDescending { it.createdAt })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { switcher?.invoke() ?: Text("Важные даты", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = "" }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Дата") })
        },
    ) { padding ->
        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Отмечайте здесь важное: «пришла награда», «начался отпуск на 17 дней», «переехали». " +
                        "Потом легко найти, когда это было, а под каждой датой видно, сколько прошло времени.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Найти: отпуск, награда…", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Очистить") } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (found.isEmpty()) {
                item(key = "none") {
                    Text("Ничего не нашлось.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
            }
            found.groupBy { LocalDate.ofEpochDay(it.momentDay!!).year }.forEach { (year, list) ->
                item(key = "y-$year") {
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { m -> MomentRow(m, ui.today) { editing = m.id } }
            }
        }
    }

    val id = editing
    if (id != null) {
        MomentDialog(
            moment = if (id.isEmpty()) null else ui.entries[id],
            today = ui.today,
            allTags = ui.allTags,
            onSave = { title, body, day, end, tags -> onSave(id.ifEmpty { null }, title, body, day, end, tags); editing = null },
            onDelete = { onDelete(id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MomentRow(m: EntryEntity, today: Long, onClick: () -> Unit) {
    val day = m.momentDay ?: return
    val counter = Moments.counter(day, m.momentEnd, today)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(m.title, style = MaterialTheme.typography.titleMedium)
        Text(
            "📍 " + Moments.describe(day, m.momentEnd),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val note = m.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(100)
        val tags = m.tagList.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
        listOfNotNull(note, tags).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            counter.main,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        counter.detail?.let {
            Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentDialog(
    moment: EntryEntity?,
    today: Long,
    allTags: Collection<String>,
    onSave: (String, String, Long, Long?, List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(moment?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(moment?.body ?: "") }
    var tags by rememberSaveable { mutableStateOf(moment?.tagList?.joinToString(", ") ?: "") }
    var day by rememberSaveable { mutableStateOf(moment?.momentDay ?: today) }
    val oldEnd = moment?.momentEnd?.takeIf { e -> moment.momentDay?.let { e > it } == true }
    var isPeriod by rememberSaveable { mutableStateOf(oldEnd != null) }
    var daysText by rememberSaveable { mutableStateOf(oldEnd?.let { e -> Moments.length(moment.momentDay!!, e).toString() } ?: "") }
    var pickDay by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val days = daysText.toIntOrNull()?.takeIf { it in 1..Moments.MAX_PERIOD_DAYS }
    val end = if (isPeriod && days != null) Moments.endOf(day, days) else null
    val canSave = title.isNotBlank() && (!isPeriod || days != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (moment == null) "Новая важная дата" else "Важная дата") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Что произошло") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = SENTENCES)
                OutlinedButton(onClick = { pickDay = true }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (isPeriod) "С " else "") + Moments.describe(day, null) + if (day == today) " (сегодня)" else "")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Период — несколько дней", modifier = Modifier.weight(1f))
                    Switch(checked = isPeriod, onCheckedChange = { isPeriod = it })
                }
                if (isPeriod) {
                    OutlinedTextField(
                        daysText,
                        { t -> daysText = t.filter(Char::isDigit).take(4) },
                        label = { Text("Сколько дней") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text(end?.let { "по " + Moments.describe(it, null) } ?: "Например, 17") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    Moments.counter(day, end, today).let { c -> listOfNotNull(c.main, c.detail).joinToString(" · ") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TagField(tags, { tags = it }, allTags, Modifier.fillMaxWidth())
                MarkdownField(body = body, onChange = { body = it }, modifier = Modifier.fillMaxWidth(), label = "Заметка")
                if (moment != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onSave(title, body, day, end, PinRepository.parseTags(tags)) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (pickDay) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Math.floorDiv(it, DAY_MS) }
                    pickDay = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text("Отмена") } },
        ) { DatePicker(state = state, showModeToggle = true) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${title.trim()}»?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
