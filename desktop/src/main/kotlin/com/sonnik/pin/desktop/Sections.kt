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

private val RU = Locale.forLanguageTag("ru")

/** Заголовок раздела с кнопкой «добавить». */
@Composable
private fun PaneHeader(title: String, subtitle: String?, addLabel: String?, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (addLabel != null) {
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(addLabel)
            }
        }
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Окно правки: поля сверху с прокруткой, кнопки снизу. */
@Composable
private fun EditDialog(title: String, height: Int, onClose: () -> Unit, content: @Composable () -> Unit) {
    DialogWindow(onCloseRequest = onClose, title = title, state = rememberDialogState(size = DpSize(560.dp, height.dp))) {
        PinTheme { content() }
    }
}

@Composable
private fun DialogBody(buttons: @Composable () -> Unit, fields: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            fields()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            buttons()
        }
    }
}

// ---------- Люди ----------

/** Человек для списка: ближайший день рождения и сколько до него. */
private data class PersonRow(val person: PersonEntity, val birthday: Edits.Birthday?, val nextDay: Long?)

private fun personRows(data: PinData, today: Long): List<PersonRow> =
    data.people.filter { !it.deleted }.map { p ->
        val b = Edits.birthdayOf(data, p.id)
        val next = b?.let { runCatching { RepeatRule.Yearly(it.month, it.day).nextAfter(today - 1, today) }.getOrNull() }
        PersonRow(p, b, next)
    }.sortedWith(compareBy<PersonRow> { it.nextDay ?: Long.MAX_VALUE }.thenBy { it.person.name })

@Composable
internal fun PeoplePane(data: PinData, today: Long, store: PinStore?) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val rows = remember(data, today) { personRows(data, today) }
    val shown = rows.filter { query.isBlank() || it.person.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            PaneHeader("Люди", "Дни рождения — ближайшие сверху", "Человек") { creating = true }
            OutlinedTextField(query, { query = it }, placeholder = { Text("Поиск по имени") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shown.isEmpty()) item { Hint(if (rows.isEmpty()) "Пока никого нет. Добавьте человека и его день рождения." else "Никого не нашлось") }
            items(shown, key = { it.person.id }) { row ->
                Card(onClick = { editing = row.person.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(row.person.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.person.name, style = MaterialTheme.typography.bodyLarge)
                            val parts = buildList {
                                add(Relation.title(row.person.relation))
                                row.birthday?.let { b -> add("🎂 " + Occasions.nextBirthdayText(b.month, b.day, b.year, today)) }
                            }
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.person.note.isNotBlank()) {
                                Text(row.person.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        row.nextDay?.let { d ->
                            val left = d - today
                            if (left <= 7) {
                                Text(
                                    if (left == 0L) "сегодня" else "через $left дн.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (store != null && (creating || editing != null)) {
        val id = editing
        EditDialog(if (id == null) "Новый человек" else "Человек", 640, onClose = { creating = false; editing = null }) {
            PersonContent(
                data, id, today,
                onSave = { name, relation, note, birthday, remind ->
                    store.commit { Edits.savePerson(it, id, name, relation, note, birthday, remind, today, System.currentTimeMillis()).first }
                    creating = false
                    editing = null
                },
                onDelete = { pid -> store.commit { Edits.deletePerson(it, pid, System.currentTimeMillis()) }; editing = null },
                onClose = { creating = false; editing = null },
            )
        }
    }
}

/** «29.11.1975» или «29.11» → день рождения. */
internal fun parseBirthday(text: String): Edits.Birthday? {
    val m = Regex("""^\s*(\d{1,2})[./-](\d{1,2})(?:[./-](\d{4}))?\s*$""").matchEntire(text) ?: return null
    val d = m.groupValues[1].toInt()
    val mo = m.groupValues[2].toInt()
    val y = m.groupValues[3].toIntOrNull()
    // 29 февраля проверяем на високосном годе.
    val ok = runCatching { LocalDate.of(y ?: 2024, mo, d) }.isSuccess
    return if (ok) Edits.Birthday(mo, d, y) else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonContent(
    data: PinData,
    id: String?,
    today: Long,
    onSave: (String, String, String, Edits.Birthday?, List<Int>) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val person = id?.let { pid -> data.people.firstOrNull { it.id == pid } }
    val birthday = remember(id) { id?.let { Edits.birthdayOf(data, it) } }
    val remindOld = remember(id) {
        data.entries.firstOrNull { !it.deleted && it.entryType == EntryType.BIRTHDAY && it.personId == id }?.remindDays
    }
    var name by remember(id) { mutableStateOf(person?.name ?: "") }
    var relation by remember(id) { mutableStateOf(person?.relation ?: Relation.FRIEND.name) }
    var note by remember(id) { mutableStateOf(person?.note ?: "") }
    var date by remember(id) { mutableStateOf(birthday?.let { b -> "${b.day}.%02d".format(b.month) + (b.year?.let { ".$it" } ?: "") } ?: "") }
    var remind by remember(id) { mutableStateOf(Occasions.parseRemindDays(remindOld ?: "7,1").joinToString(", ")) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val parsed = if (date.isBlank()) null else parseBirthday(date)

    DialogBody(
        buttons = {
            if (person != null) TextButton(onClick = { onDelete(person.id) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Напишите имя"
                    date.isNotBlank() && parsed == null -> error = "День рождения — в виде 29.11.1975 или 29.11"
                    else -> onSave(name, relation, note, parsed, Occasions.parseRemindDays(remind))
                }
            }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(name, { name = it; error = null }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Кто это")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (Relation.entries.map { it.name to it.title } + Relation.customOf(data.people.map { it.relation }).map { it to it }).forEach { (key, title) ->
                FilterChip(selected = Relation.key(relation) == Relation.key(key), onClick = { relation = key }, label = { Text(title) })
            }
        }
        OutlinedTextField(
            if (Relation.known(relation) == null) relation else "",
            { relation = it },
            label = { Text("Или своя категория") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            date, { date = it; error = null },
            label = { Text("День рождения") },
            singleLine = true,
            isError = date.isNotBlank() && parsed == null,
            supportingText = {
                Text(parsed?.let { Occasions.nextBirthdayText(it.month, it.day, it.year, today) } ?: "29.11.1975, год можно не писать")
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            remind, { remind = it },
            label = { Text("Напомнить за, дней") },
            singleLine = true,
            supportingText = { Text("через запятую, например 7, 1") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(note, { note = it }, label = { Text("Заметка: что любит, размеры…") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

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
private fun PlanCard(plan: EntryEntity, onClick: () -> Unit) {
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

// ---------- Привычки ----------

@Composable
internal fun HabitsPane(data: PinData, today: Long, store: PinStore?) {
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val habits = remember(data) { data.entries.filter { !it.deleted && it.entryType == EntryType.HABIT }.sortedBy { it.createdAt } }
    val done = remember(data) {
        data.journal.filter { it.event == JournalEvent.DONE }.groupBy({ it.entryId }, { it.day }).mapValues { it.value.toSet() }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            PaneHeader("Привычки", "Отмечайте дни — щелчок по кружку", "Привычка") { creating = true }
        }
        LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (habits.isEmpty()) item { Hint("Привычек пока нет. Например: «Зарядка», «Читать 20 страниц».") }
            items(habits, key = { it.id }) { h ->
                val info = HabitInfo(h.id, h.rule, h.repeatAnchor ?: today, done[h.id].orEmpty())
                HabitCard(h, info, today, onOpen = { editing = h.id }, onToggle = { day ->
                    store?.commit { Edits.toggleHabit(it, h.id, day, today, System.currentTimeMillis()) }
                })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (store != null && (creating || editing != null)) {
        val id = editing
        EditDialog(if (id == null) "Новая привычка" else "Привычка", 520, onClose = { creating = false; editing = null }) {
            HabitContent(
                data, id,
                onSave = { title, rule, remind ->
                    store.commit { Edits.saveHabit(it, id, title, rule, remind, today, System.currentTimeMillis()).first }
                    creating = false
                    editing = null
                },
                onDelete = { hid -> store.commit { Edits.delete(it, hid, System.currentTimeMillis()) }; editing = null },
                onClose = { creating = false; editing = null },
            )
        }
    }
}

@Composable
private fun HabitCard(h: EntryEntity, info: HabitInfo, today: Long, onOpen: () -> Unit, onToggle: (Long) -> Unit) {
    val stats = Habits.stats(info, today)
    Card(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.bodyLarge)
                val parts = buildList {
                    add(h.rule?.let(RepeatRule::describe) ?: "каждый день")
                    h.timeMinutes?.let { add("⏰ " + RuText.time(it)) }
                    if (stats.current > 0) add("🔥 ${stats.current}")
                    if (stats.best > stats.current) add("лучшее ${stats.best}")
                    stats.rate30?.let { add("${(it * 100).toInt()}% за 30 дней") }
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Неделя: семь дней до сегодня включительно.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (day in today - 6..today) {
                    val scheduled = Habits.isScheduled(info, day)
                    val isDone = day in info.doneDays
                    val scheme = MaterialTheme.colorScheme
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day == today) scheme.primary else scheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (isDone) scheme.primary else Color.Transparent)
                                .border(2.dp, if (isDone) scheme.primary else if (scheduled) scheme.outline else scheme.outlineVariant, CircleShape)
                                .clickable { onToggle(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) Text("✓", color = scheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitContent(
    data: PinData,
    id: String?,
    onSave: (String, RepeatRule?, Int?) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val habit = id?.let(data.byId::get)
    val codes = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val oldDays = (habit?.rule as? RepeatRule.Weekly)?.days ?: DayOfWeek.entries.toSet()
    var title by remember(id) { mutableStateOf(habit?.title ?: "") }
    var days by remember(id) { mutableStateOf(oldDays) }
    var remind by remember(id) { mutableStateOf(habit?.timeMinutes?.let(RuText::time) ?: "") }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val time = if (remind.isBlank()) null else DateText.parseTime(remind)
    DialogBody(
        buttons = {
            if (habit != null) TextButton(onClick = { onDelete(habit.id) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(onClick = {
                when {
                    title.isBlank() -> error = "Напишите привычку"
                    days.isEmpty() -> error = "Выберите хотя бы один день"
                    remind.isNotBlank() && time == null -> error = "Время — в виде 8:30"
                    else -> {
                        val rule = if (days.size == 7) null else RepeatRule.parse("weekly " + DayOfWeek.entries.filter { it in days }.joinToString(",") { codes[it.value - 1] })
                        onSave(title, rule, time)
                    }
                }
            }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(title, { title = it; error = null }, label = { Text("Привычка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("По каким дням")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { dow ->
                FilterChip(
                    selected = dow in days,
                    onClick = { days = if (dow in days) days - dow else days + dow },
                    label = { Text(dow.getDisplayName(TextStyle.SHORT_STANDALONE, RU)) },
                )
            }
        }
        OutlinedTextField(
            remind, { remind = it; error = null },
            label = { Text("Напомнить в") },
            singleLine = true,
            supportingText = { Text("напоминает телефон; пусто — без напоминания") },
            modifier = Modifier.width(260.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------- Настройки ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPane(path: String?, onChoose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PaneHeader("Настройки", "Pin $APP_VERSION для Windows", null) {}
            FieldLabel("Файл")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(path ?: "Не выбран", style = MaterialTheme.typography.bodyLarge)
                        Hint("Pin.zip из папки Google Диска — тот же, что выбран в телефоне.")
                    }
                    OutlinedButton(onClick = onChoose) { Text("Выбрать…") }
                }
            }
            FieldLabel("Тема")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = Appearance.mode == m, onClick = { Appearance.update(mode = m) }, label = { Text(m.title) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Чёрный фон в тёмной теме", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = Appearance.pureBlack, onCheckedChange = { Appearance.update(pureBlack = it) })
            }
            FieldLabel("Палитра")
            val dark = isDark()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (ThemePalette.entries - ThemePalette.DYNAMIC).forEach { p ->
                    val scheme = staticColorScheme(p, dark)
                    val selected = Appearance.palette == p
                    Column(
                        Modifier.width(120.dp).clip(RoundedCornerShape(14.dp))
                            .background(scheme.background)
                            .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable { Appearance.update(palette = p) }
                            .padding(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(scheme.primary, scheme.tertiary, scheme.surfaceContainerHigh).forEach { c ->
                                Box(Modifier.size(18.dp).clip(CircleShape).background(c))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(p.title, color = scheme.onBackground, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Hint("Оформление хранится на этом компьютере и в телефон не передаётся.")
        }
    }
}
