package com.sonnik.pin.desktop

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Feature
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.Search
import com.sonnik.pin.engine.SearchFilter
import com.sonnik.pin.engine.SearchItem
import com.sonnik.pin.engine.SearchState
import com.sonnik.pin.engine.TaskStatus

/** Записи для поиска: с именем человека, поводом и списком (как на телефоне). */
internal fun PinData.searchItems(): List<SearchItem> {
    val names = people.associate { it.id to it.name }
    return entries.filter { !it.deleted && !it.disabled }.map { e ->
        val extra = listOfNotNull(
            e.personId?.let(names::get),
            e.occasionId?.let { byId[it]?.title },
            e.price,
            e.listId?.let { listsById[it]?.name },
        ).joinToString(" ")
        SearchItem(e.id, e.entryType, e.title, e.body, e.tagList, e.entryPriority, e.entryStatus, e.dueDay ?: e.momentDay, extra)
    }
}

private fun typeLabel(e: EntryEntity): String = when (e.entryType) {
    EntryType.ONE_TIME -> "задача"
    EntryType.RECURRING -> "повтор"
    EntryType.PLAN -> "план"
    EntryType.BIRTHDAY -> "день рождения"
    EntryType.GIFT -> "подарок"
    EntryType.HOLIDAY -> "праздник"
    EntryType.HABIT -> "привычка"
    EntryType.MOMENT -> "важная дата"
}

/** «Поиск» и «Архив выполненных»: фильтры по состоянию, типу, важности, списку и тегу. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchPane(data: PinData, today: Long, initialState: SearchState, key: Int) {
    val actions = LocalActions.current
    var query by remember(key) { mutableStateOf("") }
    var type by remember(key) { mutableStateOf<EntryType?>(null) }
    var tag by remember(key) { mutableStateOf<String?>(null) }
    var important by remember(key) { mutableStateOf(false) }
    var state by remember(key) { mutableStateOf(initialState) }
    var list by remember(key) { mutableStateOf<String?>(null) }
    val filter = SearchFilter(query, setOfNotNull(type), tag, important, state)
    val all = remember(data) { data.searchItems() }
    val tags = remember(data) { data.entries.filter { !it.deleted }.flatMap { it.tagList }.distinct().sorted() }
    val lists = data.lists.filter { !it.deleted }.sortedBy { it.sortOrder }
    val results = remember(all, filter, today, list) {
        Search.filter(all, filter, today) { data.byId[it]?.rollovers ?: 0 }
            .let { found -> if (list == null) found else found.filter { data.byId[it.id]?.listId == list } }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state == SearchState.DONE) "Архив и поиск" else "Поиск", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (!filter.isEmpty || list != null) {
                    TextButton(onClick = { query = ""; type = null; tag = null; important = false; state = SearchState.ALL; list = null }) { Text("Сбросить") }
                }
            }
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Название, описание, тег, человек") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SearchState.entries.forEach { s -> FilterChip(selected = state == s, onClick = { state = s }, label = { Text(s.title) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = important, onClick = { important = !important }, label = { Text("Важные") })
                listOf(EntryType.ONE_TIME, EntryType.RECURRING, EntryType.PLAN, EntryType.MOMENT, EntryType.BIRTHDAY, EntryType.GIFT, EntryType.HOLIDAY).forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = if (type == t) null else t }, label = { Text(Search.typeTitle(t)) })
                }
            }
            if (lists.isNotEmpty() && Feature.LISTS.on()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lists.forEach { l ->
                        FilterChip(
                            selected = list == l.id,
                            onClick = { list = if (list == l.id) null else l.id },
                            label = { Text(l.name) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(listColor(l.color))) },
                        )
                    }
                }
            }
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { t -> FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text("#$t") }) }
                }
            }
            Text("Найдено: ${results.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            if (results.isEmpty()) item { Box(Modifier.height(160.dp)) { Centered("Ничего не найдено") } }
            items(results, key = { it.id }) { item ->
                val e = data.byId[item.id] ?: return@items
                ResultRow(e, data, today) { actions.open(e) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ResultRow(e: EntryEntity, data: PinData, today: Long, onClick: () -> Unit) {
    val done = e.entryStatus == TaskStatus.DONE
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                data.titleOf(e),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                add(typeLabel(e))
                if (done) e.completedDay?.let { add("выполнено " + RuText.relativeDayWithDate(it, today)) }
                else e.dueDay?.let { add(RuText.relativeDayWithDate(it, today)) }
                if (e.entryPriority == Priority.HIGH) add("важно")
                e.rule?.takeIf { e.entryType == EntryType.RECURRING }?.let { add("↻ " + RepeatRule.describe(it)) }
                e.listId?.let(data.listsById::get)?.let { add("● " + it.name) }
                e.tagList.forEach { add("#$it") }
            }
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Разбор перенесённых ----------

/** Окно «Разбор перенесённых»: по одной задаче — сегодня, завтра, на дату, сделано, в планы, удалить. */
@Composable
internal fun ReviewWindow(data: PinData, today: Long, onClose: () -> Unit) {
    DialogWindow(onCloseRequest = onClose, title = "Разбор перенесённых", state = rememberDialogState(size = DpSize(560.dp, 620.dp))) {
        PinTheme { ReviewContent(data, today, LocalActions.current, onClose) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewContent(data: PinData, today: Long, actions: Actions, onClose: () -> Unit) {
    // Очередь фиксируется при открытии: разобранное уходит, новое не добавляется.
    val queue = remember { data.board(today).rolledOver.map { it.id } }
    var index by remember { mutableIntStateOf(0) }
    var dateText by remember { mutableStateOf(DateText.format(today + 1)) }
    val position = (index until queue.size).firstOrNull { data.byId[queue[it]]?.let { e -> !e.deleted } == true } ?: queue.size
    val entry = queue.getOrNull(position)?.let(data.byId::get)
    fun next() { index = position + 1 }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (queue.isNotEmpty()) {
            LinearProgressIndicator(progress = { position.toFloat() / queue.size }, modifier = Modifier.fillMaxWidth())
            Text(
                if (entry != null) "${position + 1} из ${queue.size}" else "Разобрано: ${queue.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📌", style = MaterialTheme.typography.displayMedium)
                    Text(if (queue.isEmpty()) "Перенесённых задач нет." else "Готово! Хвосты разобраны.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onClose) { Text("К задачам") }
                }
            }
            return@Column
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.title, style = MaterialTheme.typography.headlineSmall)
                val meta = buildList {
                    add(RuText.rollovers(entry.rollovers))
                    entry.originalDay?.let { add("изначально на ${RuText.relativeDayWithDate(it, today)}") }
                    entry.rule?.let { add("↻ " + RepeatRule.describe(it)) }
                }
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                if (entry.body.isNotBlank()) MarkdownText(entry.body)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.reschedule(entry, today); next() }) { Text("Оставить на сегодня") }
            OutlinedButton(onClick = { actions.reschedule(entry, today + 1); next() }) { Text("На завтра") }
            OutlinedButton(onClick = { actions.toggle(entry, true); next() }) { Text("Сделано") }
            if (entry.rule != null) {
                OutlinedButton(onClick = { actions.skip(entry); next() }) { Text("Пропустить повтор") }
            } else {
                OutlinedButton(onClick = { actions.toPlans(entry); next() }) { Text("В планы") }
            }
            TextButton(onClick = { actions.delete(entry); next() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val day = DateText.parse(dateText, today)
            OutlinedTextField(dateText, { dateText = it }, label = { Text("На дату") }, singleLine = true, isError = day == null, modifier = Modifier.width(170.dp))
            OutlinedButton(onClick = { if (day != null) { actions.reschedule(entry, day); next() } }, enabled = day != null) { Text("Перенести") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { next() }) { Text("Пропустить →") }
        }
    }
}
