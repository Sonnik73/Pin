package com.sonnik.pin.ui

import com.sonnik.pin.engine.Markdown
import com.sonnik.pin.engine.RuText
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.PlanScope
import java.time.LocalDate

private const val DAY_MS = 24L * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    ui: PinUi,
    onSave: (id: String?, title: String, body: String, scope: PlanScope, tags: List<String>) -> Unit,
    onToTask: (id: String, day: Long, time: Int?, title: String, body: String, scope: PlanScope, tags: List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    onBodyChange: (String, String) -> Unit = { _, _ -> },
    /** Какие планы раскрыты сразу (для скриншотов). */
    expandedIds: Set<String> = emptySet(),
    /** Переключатель «Планы / Важные даты» вместо заголовка. */
    switcher: (@Composable () -> Unit)? = null,
) {
    // null — диалог закрыт; "" — новый план.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val plans = ui.entries.values.filter { it.entryType == EntryType.PLAN }
        .groupBy { PlanScope.parse(it.planScope) }
        .toSortedMap(compareBy { PlanScope.sortKey(it) })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { switcher?.invoke() ?: Text("Планы", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = "" }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("План") })
        },
    ) { padding ->
        if (plans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyIllustration(EmptyArt.PLANS, Modifier.padding(bottom = 16.dp))
                Text(
                    "Здесь живут дела без точной даты: «когда-нибудь», в этом месяце, в следующем году. " +
                        "Когда придёт время — превратите план в задачу.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                plans.forEach { (scope, list) ->
                    item(key = "h-" + PlanScope.format(scope)) {
                        Text(
                            PlanScope.describe(scope).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(list.sortedBy { it.createdAt }, key = { it.id }) { plan ->
                        var expanded by rememberSaveable(plan.id) { mutableStateOf(plan.id in expandedIds) }
                        Column(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().clickable { editing = plan.id }.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp)) {
                                Text(plan.title)
                                val tags = plan.tagList.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
                                // Первая строка описания — только пока оно свёрнуто и это не список.
                                val firstLine = plan.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80)
                                    ?.takeIf { !expanded && Markdown.checklist(plan.body).second == 0 && !it.startsWith("- ") && !it.startsWith("* ") }
                                val extra = listOfNotNull(firstLine, tags)
                                if (extra.isNotEmpty()) {
                                    Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                BodyToggle(plan.body, expanded, { expanded = it }, Modifier.padding(top = 4.dp))
                            }
                            if (expanded && plan.body.isNotBlank()) {
                                ExpandedBody(plan.body, { onBodyChange(plan.id, it) }, Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, bottom = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    val id = editing
    if (id != null) {
        PlanDialog(
            plan = if (id.isEmpty()) null else ui.entries[id],
            today = ui.today,
            onSave = { title, body, scope, tags -> onSave(id.ifEmpty { null }, title, body, scope, tags); editing = null },
            onToTask = { day, time, title, body, scope, tags -> onToTask(id, day, time, title, body, scope, tags); editing = null },
            onDelete = { onDelete(id); editing = null },
            onDismiss = { editing = null },
            allTags = ui.allTags,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanDialog(
    plan: EntryEntity?,
    today: Long,
    onSave: (String, String, PlanScope, List<String>) -> Unit,
    onToTask: (Long, Int?, String, String, PlanScope, List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    allTags: Collection<String> = emptyList(),
) {
    val now = LocalDate.ofEpochDay(today)
    var title by rememberSaveable { mutableStateOf(plan?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(plan?.body ?: "") }
    var tags by rememberSaveable { mutableStateOf(plan?.tagList?.joinToString(", ") ?: "") }
    var scopeText by rememberSaveable { mutableStateOf(plan?.planScope ?: "someday") }
    var taskDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickDay by rememberSaveable { mutableStateOf(false) }
    val scope = PlanScope.parse(scopeText)
    // Варианты горизонта: когда-нибудь, ближайшие 6 месяцев, этот и следующий год.
    val options = buildList<PlanScope> {
        add(PlanScope.Someday)
        for (i in 0 until 6) now.plusMonths(i.toLong()).let { add(PlanScope.Month(it.year, it.monthValue)) }
        add(PlanScope.Year(now.year))
        add(PlanScope.Year(now.year + 1))
        if (scope !in this) add(scope)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "Новый план" else "План") },
        // Окно учитывает клавиатуру: сжимается над ней, а поле прокручивается к курсору.
        properties = KeyboardAwareDialog,
        modifier = Modifier.keyboardAware(),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Что сделать") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = SENTENCES)
                Text("Когда", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { o ->
                        FilterChip(
                            selected = o == scope,
                            onClick = { scopeText = PlanScope.format(o) },
                            label = { Text(PlanScope.describe(o)) },
                        )
                    }
                }
                TagField(tags, { tags = it }, allTags, Modifier.fillMaxWidth())
                MarkdownField(body = body, onChange = { body = it }, modifier = Modifier.fillMaxWidth())
                if (plan != null) {
                    TextButton(onClick = { pickDay = true }) { Text("Превратить в задачу…") }
                    TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, body, scope, PinRepository.parseTags(tags)) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (pickDay) {
        val state = rememberDatePickerState(initialSelectedDateMillis = PlanScope.suggestedDay(scope, today) * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = {
                    // Правки плана сохраняются вместе с переводом в задачу.
                    // Следующий шаг — время (можно без времени).
                    state.selectedDateMillis?.let { taskDay = Math.floorDiv(it, DAY_MS) }
                    pickDay = false
                }) { Text("Дальше") }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text("Отмена") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }

    taskDay?.let { day ->
        val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { taskDay = null },
            title = { Text("Время задачи · ${RuText.relativeDayWithDate(day, today)}") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onToTask(day, timeState.hour * 60 + timeState.minute, title, body, scope, PinRepository.parseTags(tags))
                    taskDay = null
                }) { Text("В задачу") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onToTask(day, null, title, body, scope, PinRepository.parseTags(tags))
                    taskDay = null
                }) { Text("Без времени") }
            },
        )
    }
}
