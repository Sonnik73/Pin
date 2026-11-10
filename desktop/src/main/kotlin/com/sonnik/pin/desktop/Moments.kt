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
                        )
                    }
                }
            }
            if (group != null && inGroup.isNotEmpty()) {
                val s = Moments.summary(inGroup.map { it.momentDay!! to it.momentEnd }, today)
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📁 $group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(s.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
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
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { m -> MomentCard(m, today) { editing = m.id } }
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
                onSave = { title, body, day, end, tags, g ->
                    store.commit { Edits.saveMoment(it, id, title, body, day, end, tags, System.currentTimeMillis(), g).first }
                    creating = false
                    editing = null
                },
                onDelete = { mid -> store.commit { Edits.delete(it, mid, System.currentTimeMillis()) }; editing = null },
                onClose = { creating = false; editing = null },
            )
        }
    }
}

@Composable
internal fun MomentCard(m: EntryEntity, today: Long, onClick: () -> Unit) {
    val day = m.momentDay ?: return
    val counter = Moments.counter(day, m.momentEnd, today)
    Card(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(m.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("📍 " + Moments.describe(day, m.momentEnd) + (m.momentGroup?.let { "  ·  📁 $it" } ?: ""), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val note = m.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(140)
            val tags = m.tagList.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
            listOfNotNull(note, tags).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(counter.main, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            counter.detail?.let {
                Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
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
    onSave: (String, String, Long, Long?, List<String>, String?) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val m = id?.let(data.byId::get)
    val oldEnd = m?.momentEnd?.takeIf { e -> m.momentDay?.let { e > it } == true }
    var title by remember(id) { mutableStateOf(m?.title ?: "") }
    var dateText by remember(id) { mutableStateOf(DateText.format(m?.momentDay ?: today)) }
    var isPeriod by remember(id) { mutableStateOf(oldEnd != null) }
    var daysText by remember(id) { mutableStateOf(oldEnd?.let { Moments.length(m.momentDay!!, it).toString() } ?: "") }
    var tags by remember(id) { mutableStateOf(m?.tagList?.joinToString(", ") ?: "") }
    var group by remember(id) { mutableStateOf(if (m == null) newGroup.orEmpty() else m.momentGroup.orEmpty()) }
    var body by remember(id) { mutableStateOf(m?.body ?: "") }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val day = DateText.parse(dateText, today)
    val days = daysText.toIntOrNull()?.takeIf { it in 1..Moments.MAX_PERIOD_DAYS }
    val end = if (isPeriod && day != null && days != null) Moments.endOf(day, days) else null

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
                    isPeriod && days == null -> "Сколько дней длится период?"
                    else -> null
                }
                if (error == null && day != null) {
                    onSave(
                        title, body, day, end,
                        tags.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
                        Moments.groupName(group),
                    )
                }
            }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(title, { title = it; error = null }, label = { Text("Что произошло") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                dateText, { dateText = it; error = null },
                label = { Text(if (isPeriod) "С какого дня" else "Дата") },
                singleLine = true, isError = day == null,
                modifier = Modifier.width(180.dp),
            )
            Text("Период", modifier = Modifier.padding(start = 8.dp))
            Switch(checked = isPeriod, onCheckedChange = { isPeriod = it; error = null })
            if (isPeriod) {
                OutlinedTextField(
                    daysText, { t -> daysText = t.filter(Char::isDigit).take(4); error = null },
                    label = { Text("Дней") }, singleLine = true, isError = daysText.isNotEmpty() && days == null,
                    modifier = Modifier.width(100.dp),
                )
            }
        }
        if (day != null) {
            val c = Moments.counter(day, end, today)
            Text(
                listOfNotNull(Moments.describe(day, end), c.main, c.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
