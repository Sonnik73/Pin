package com.sonnik.pin.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Moments
import com.sonnik.pin.engine.Search
import java.time.LocalDate

/** Важные даты и периоды: по годам, новые сверху, поиск; под каждой — счётчик «прошло…». */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MomentsPane(data: PinData, today: Long, store: PinStore?, initialGroup: String? = null) {
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(initialGroup) }
    val all = remember(data) { data.entries.filter { !it.deleted && it.entryType == EntryType.MOMENT && it.momentDay != null } }
    val groups = remember(all) { Moments.groups(all.map { it.momentGroup }) }
    val inGroup = if (group == null) all else all.filter { Moments.sameGroup(it.momentGroup, group) }
    val found = inGroup.filter { Search.matchesAll(query, listOf(it.title, it.body, it.tagList.joinToString(" "), it.momentGroup)) }
        .sortedWith(compareByDescending<EntryEntity> { it.momentDay }.thenByDescending { it.createdAt })
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            PaneHeader("Важные даты", "События и периоды — когда это было и сколько прошло", "Дата") { creating = true }
            if (all.isNotEmpty()) {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Найти: отпуск, награда…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            if (groups.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = group == null, onClick = { group = null }, label = { Text("Все") })
                    groups.forEach { g ->
                        androidx.compose.material3.FilterChip(
                            selected = Moments.sameGroup(group, g),
                            onClick = { group = if (Moments.sameGroup(group, g)) null else g },
                            label = { Text(g) },
                            leadingIcon = { com.sonnik.pin.ui.GroupDot(com.sonnik.pin.ui.groupColor(g)) },
                        )
                    }
                }
            }
            if (group != null && inGroup.isNotEmpty()) {
                val s = Moments.summary(inGroup.map { Moments.Item(it.momentDay!!, it.momentEnd, it.momentOngoing) }, today)
                com.sonnik.pin.ui.GroupSummaryCard(
                    groups.firstOrNull { Moments.sameGroup(it, group) } ?: group!!,
                    s,
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
        LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (all.isEmpty()) {
                item {
                    Hint(
                        "Отмечайте важное: «пришла награда», «начался отпуск на 17 дней», «переехали». " +
                            "Потом легко найти, когда это было, а под каждой датой видно, сколько прошло времени.",
                    )
                }
            } else if (found.isEmpty()) {
                item { Hint("Ничего не нашлось.") }
            }
            found.groupBy { LocalDate.ofEpochDay(it.momentDay!!).year }.forEach { (year, list) ->
                item(key = "y:$year") {
                    Text(
                        "$year · ${list.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { m -> com.sonnik.pin.ui.MomentCard(m, today, onClick = { editing = m.id }) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (store != null && (creating || editing != null)) {
        val id = editing
        EditDialog(if (id == null) "Новая важная дата" else "Важная дата", 640, onClose = { creating = false; editing = null }) {
            MomentContent(
                data, id, today,
                groups = groups,
                newGroup = group,
                onSave = { title, body, day, end, tags, g, ongoing, remind ->
                    store.commit { Edits.saveMoment(it, id, title, body, day, end, tags, System.currentTimeMillis(), g, ongoing, remind).first }
                    creating = false
                    editing = null
                },
                onDelete = { mid -> store.commit { Edits.delete(it, mid, System.currentTimeMillis()) }; editing = null },
                onClose = { creating = false; editing = null },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MomentContent(
    data: PinData,
    id: String?,
    today: Long,
    groups: List<String> = emptyList(),
    newGroup: String? = null,
    onSave: (String, String, Long, Long?, List<String>, String?, Boolean, Moments.Remind?) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val m = id?.let(data.byId::get)
    val oldEnd = m?.momentEnd?.takeIf { e -> m.momentDay?.let { e > it } == true }
    var title by remember(id) { mutableStateOf(m?.title ?: "") }
    var dateText by remember(id) { mutableStateOf(DateText.format(m?.momentDay ?: today)) }
    var ongoing by remember(id) { mutableStateOf(m?.momentOngoing == true) }
    var isPeriod by remember(id) { mutableStateOf(oldEnd != null || ongoing) }
    var daysText by remember(id) { mutableStateOf(oldEnd?.let { Moments.length(m.momentDay!!, it).toString() } ?: "") }
    var endText by remember(id) { mutableStateOf(oldEnd?.let(DateText::format) ?: "") }
    var tags by remember(id) { mutableStateOf(m?.tagList?.joinToString(", ") ?: "") }
    var group by remember(id) { mutableStateOf(if (m == null) newGroup.orEmpty() else m.momentGroup.orEmpty()) }
    var body by remember(id) { mutableStateOf(m?.body ?: "") }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    var remind by remember(id) { mutableStateOf(Moments.parseRemind(m?.momentRemind)) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val day = DateText.parse(dateText, today)
    val days = daysText.toIntOrNull()?.takeIf { it in 1..Moments.MAX_PERIOD_DAYS }
    val running = isPeriod && ongoing
    val end = if (isPeriod && !ongoing && day != null && days != null) Moments.endOf(day, days) else null

    DialogBody(
        buttons = {
            if (m != null) {
                if (confirmDelete) {
                    Text("Удалить?", modifier = Modifier.align(Alignment.CenterVertically))
                    TextButton(onClick = { onDelete(m.id) }) { Text("Да, удалить", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Нет") }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(onClick = {
                error = when {
                    title.isBlank() -> "Напишите, что произошло"
                    day == null -> "Проверьте дату: например, 10.11.2026"
                    isPeriod && !ongoing && days == null -> "Укажите, сколько дней длится период или по какое число"
                    else -> null
                }
                if (error == null && day != null) {
                    onSave(
                        title, body, day, end,
                        tags.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
                        Moments.groupName(group),
                        running,
                        remind,
                    )
                }
            }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(title, { title = it; error = null }, label = { Text("Что произошло") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                dateText,
                { t ->
                    dateText = t
                    error = null
                    // Сдвинули начало — конец едет следом, длина та же.
                    val s = DateText.parse(t, today)
                    val n = daysText.toIntOrNull()?.takeIf { it in 1..Moments.MAX_PERIOD_DAYS }
                    if (s != null && n != null) endText = DateText.format(Moments.endOf(s, n))
                },
                label = { Text(if (isPeriod) "С какого дня" else "Дата") },
                singleLine = true, isError = day == null,
                modifier = Modifier.width(180.dp),
            )
            Text("Период", modifier = Modifier.padding(start = 8.dp))
            Switch(checked = isPeriod, onCheckedChange = { isPeriod = it; error = null })
        }
        if (isPeriod) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = ongoing,
                onCheckedChange = { on ->
                    ongoing = on
                    error = null
                    // Сняли «по настоящее время» — значит, закончилось: по умолчанию сегодня.
                    if (!on && day != null && daysText.toIntOrNull() == null) {
                        daysText = maxOf(1L, today - day + 1).toString()
                        endText = DateText.format(maxOf(day, today))
                    }
                },
            )
            Text("по настоящее время")
            if (!ongoing) {
                OutlinedTextField(
                    daysText,
                    { t ->
                        daysText = t.filter(Char::isDigit).take(4)
                        error = null
                        val n = daysText.toIntOrNull()?.takeIf { it in 1..Moments.MAX_PERIOD_DAYS }
                        if (day != null && n != null) endText = DateText.format(Moments.endOf(day, n))
                    },
                    label = { Text("Дней") }, singleLine = true, isError = daysText.isNotEmpty() && days == null,
                    modifier = Modifier.width(90.dp),
                )
                OutlinedTextField(
                    endText,
                    { t ->
                        endText = t
                        error = null
                        val e = DateText.parse(t, today)
                        if (day != null && e != null && e >= day) daysText = Moments.length(day, Moments.range(day, e).second).toString()
                    },
                    label = { Text("По") }, singleLine = true,
                    isError = endText.isNotBlank() && DateText.parse(endText, today).let { it == null || (day != null && it < day) },
                    modifier = Modifier.width(150.dp),
                )
            }
        }
        if (running) Hint("Когда закончится — снимите галочку и укажите дату окончания.")
        if (day != null) {
            val c = Moments.counter(day, end, today, running)
            Text(
                listOfNotNull(Moments.describe(day, end, running, today), c.main, c.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (day != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Checkbox(
                    checked = remind != null,
                    onCheckedChange = { on -> remind = if (on) Moments.Remind(Moments.Every.YEAR, LocalDate.ofEpochDay(day).dayOfWeek.value) else null },
                )
                Text("Напоминать каждый…")
                remind?.let { r ->
                    listOf(Moments.Every.WEEK to "неделю", Moments.Every.MONTH to "месяц", Moments.Every.YEAR to "год").forEach { (every, label) ->
                        androidx.compose.material3.FilterChip(selected = r.every == every, onClick = { remind = r.copy(every = every) }, label = { Text(label) })
                    }
                }
            }
            remind?.let { r ->
                if (r.every == Moments.Every.WEEK) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 48.dp)) {
                        Moments.WEEKDAYS_SHORT.forEachIndexed { i, name ->
                            androidx.compose.material3.FilterChip(selected = r.weekday == i + 1, onClick = { remind = r.copy(weekday = i + 1) }, label = { Text(name) })
                        }
                    }
                }
                Text(
                    Moments.describeRemind(r, day) + ", утром — вместе со сводкой",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
        }
        OutlinedTextField(group, { group = it }, label = { Text("Группа — например, «Отпуска»") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val others = groups.filterNot { Moments.sameGroup(it, group) }
        if (others.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { g -> androidx.compose.material3.AssistChip(onClick = { group = g }, label = { Text(g) }) }
            }
        }
        OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("Заметка (markdown)") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
