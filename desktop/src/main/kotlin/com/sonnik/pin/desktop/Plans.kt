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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Feature
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.Holidays
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.HabitInfo
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.ui.ThemeMode
import com.sonnik.pin.ui.ThemePalette
import com.sonnik.pin.ui.staticColorScheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

// ---------- Планы ----------

@Composable
internal fun PlansPane(data: PinData, today: Long, store: PinStore?) {
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val plans = remember(data) {
        data.entries.filter { !it.deleted && it.entryType == EntryType.PLAN }
            .groupBy { PlanScope.parse(it.planScope) }
            .toSortedMap(compareBy { PlanScope.sortKey(it) })
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            PaneHeader("Планы", "Без точной даты: на месяц, год или когда-нибудь", "План") { creating = true }
        }
        LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (plans.isEmpty()) item { Hint("Планов пока нет. Запишите то, что хочется сделать, но без даты.") }
            plans.forEach { (scope, list) ->
                item(key = "h:" + PlanScope.format(scope)) {
                    Text(
                        PlanScope.describe(scope).replaceFirstChar { it.uppercase() } + " · ${list.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(list.sortedBy { it.createdAt }, key = { it.id }) { plan -> PlanCard(plan) { editing = plan.id } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (store != null && (creating || editing != null)) {
        val id = editing
        EditDialog(if (id == null) "Новый план" else "План", 720, onClose = { creating = false; editing = null }) {
            PlanContent(
                data, id, today,
                onSave = { title, body, scope, tags ->
                    store.commit { Edits.savePlan(it, id, title, body, scope, tags, System.currentTimeMillis()).first }
                    creating = false
                    editing = null
                },
                onToTask = { pid, day, time ->
                    store.commit { Edits.planToTask(it, pid, day, time, System.currentTimeMillis()) }
                    editing = null
                },
                onDelete = { pid -> store.commit { Edits.delete(it, pid, System.currentTimeMillis()) }; editing = null },
                onClose = { creating = false; editing = null },
            )
        }
    }
}

@Composable
internal fun PlanCard(plan: EntryEntity, onClick: () -> Unit) {
    var expanded by remember(plan.id) { mutableStateOf(false) }
    Card(onClick = onClick) {
        Column {
            Text(plan.title, style = MaterialTheme.typography.bodyLarge)
            val meta = plan.tagList.joinToString("  ") { "#$it" }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.body.isNotBlank()) {
                    Text(
                        (if (expanded) "▾ " else "▸ ") + "описание",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { expanded = !expanded }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                MarkdownText(plan.body)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanContent(
    data: PinData,
    id: String?,
    today: Long,
    onSave: (String, String, PlanScope, List<String>) -> Unit,
    onToTask: (String, Long, Int?) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val plan = id?.let(data.byId::get)
    var title by remember(id) { mutableStateOf(plan?.title ?: "") }
    var body by remember(id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(plan?.body ?: "")) }
    var scope by remember(id) { mutableStateOf(PlanScope.parse(plan?.planScope)) }
    var tags by remember(id) { mutableStateOf(plan?.tagList?.joinToString(", ") ?: "") }
    var taskDate by remember(id) { mutableStateOf(DateText.format(PlanScope.suggestedDay(scope, today))) }
    var taskTime by remember(id) { mutableStateOf("") }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val d = LocalDate.ofEpochDay(today)

    DialogBody(
        buttons = {
            if (plan != null) TextButton(onClick = { onDelete(plan.id) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(onClick = {
                if (title.isBlank()) error = "Напишите, что запланировано"
                else onSave(title, body.text, scope, tags.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() })
            }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(title, { title = it; error = null }, label = { Text("Что запланировано") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Когда")
        val next = d.plusMonths(1)
        val options = listOf(
            PlanScope.Month(d.year, d.monthValue),
            PlanScope.Month(next.year, next.monthValue),
            PlanScope.Year(d.year),
            PlanScope.Year(d.year + 1),
            PlanScope.Someday,
        ).let { if (scope in it) it else it + scope }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(PlanScope.describe(s)) }) }
        }
        OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = body,
            onValueChange = { new ->
                val cont = com.sonnik.pin.engine.Markdown.continueList(body.text, new.text, new.selection.start)
                body = if (cont != null) androidx.compose.ui.text.input.TextFieldValue(cont.first, androidx.compose.ui.text.TextRange(cont.second)) else new
            },
            label = { Text("Описание (markdown)") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        if (plan != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FieldLabel("Сделать задачей")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                val day = DateText.parse(taskDate, today)
                val time = if (taskTime.isBlank()) null else DateText.parseTime(taskTime)
                OutlinedTextField(taskDate, { taskDate = it }, label = { Text("Дата") }, singleLine = true, isError = day == null, modifier = Modifier.width(160.dp))
                OutlinedTextField(taskTime, { taskTime = it }, label = { Text("Время") }, singleLine = true, isError = taskTime.isNotBlank() && time == null, modifier = Modifier.width(120.dp))
                OutlinedButton(
                    onClick = { if (day != null && (taskTime.isBlank() || time != null)) onToTask(plan.id, day, time) else error = "Проверьте дату и время" },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("В задачи") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
