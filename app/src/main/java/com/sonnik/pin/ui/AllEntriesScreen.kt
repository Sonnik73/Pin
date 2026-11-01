package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.Search
import com.sonnik.pin.engine.SearchFilter
import com.sonnik.pin.engine.SearchItem
import com.sonnik.pin.engine.SearchState
import com.sonnik.pin.engine.TaskStatus

/** Записи в виде, удобном для поиска: с именем человека и названием повода. */
fun PinUi.searchItems(): List<SearchItem> {
    val names = people.associate { it.id to it.name }
    return entries.values.filter { !it.disabled }.map { e ->
        val extra = listOfNotNull(
            e.personId?.let(names::get),
            e.occasionId?.let { entries[it]?.title },
            e.price,
            e.listId?.let { listsById[it]?.name },
        ).joinToString(" ")
        SearchItem(e.id, e.entryType, e.title, e.body, e.tagList, e.entryPriority, e.entryStatus, e.dueDay, extra)
    }
}

/** Поиск и «Все записи» с фильтрами по типу, тегу, важности и состоянию. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllEntriesScreen(ui: PinUi, onOpen: (String) -> Unit, onClose: () -> Unit, initialState: SearchState = SearchState.ALL) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<EntryType?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var important by rememberSaveable { mutableStateOf(false) }
    var state by rememberSaveable { mutableStateOf(initialState) }
    var list by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = SearchFilter(query, setOfNotNull(type), tag, important, state)
    val all = remember(ui) { ui.searchItems() }
    val results = remember(all, filter, ui.today, list) {
        Search.filter(all, filter, ui.today) { ui.entries[it]?.rollovers ?: 0 }
            .let { found -> if (list == null) found else found.filter { ui.entries[it.id]?.listId == list } }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialState == SearchState.DONE) "Архив выполненных" else "Все записи") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (!filter.isEmpty || list != null) {
                        TextButton(onClick = { query = ""; type = null; tag = null; important = false; state = SearchState.ALL; list = null }) {
                            Text("Сбросить")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "search") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Название, описание, тег, человек", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Очистить") }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    ChipRow {
                        SearchState.entries.forEach { s ->
                            FilterChip(selected = state == s, onClick = { state = s }, label = { Text(s.title) })
                        }
                    }
                    ChipRow {
                        FilterChip(selected = important, onClick = { important = !important }, label = { Text("Важные") })
                        listOf(EntryType.ONE_TIME, EntryType.RECURRING, EntryType.PLAN, EntryType.BIRTHDAY, EntryType.GIFT, EntryType.HOLIDAY)
                            .forEach { t ->
                                FilterChip(selected = type == t, onClick = { type = if (type == t) null else t }, label = { Text(Search.typeTitle(t)) })
                            }
                    }
                    if (ui.lists.isNotEmpty()) {
                        ChipRow {
                            ui.lists.forEach { l ->
                                FilterChip(
                                    selected = list == l.id,
                                    onClick = { list = if (list == l.id) null else l.id },
                                    label = { Text(l.name) },
                                    leadingIcon = { ListDot(l) },
                                )
                            }
                        }
                    }
                    if (ui.allTags.isNotEmpty()) {
                        ChipRow {
                            ui.allTags.forEach { t ->
                                FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text("#$t") })
                            }
                        }
                    }
                    Text(
                        "Найдено: ${results.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (results.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(results, key = { it.id }) { item ->
                val entry = ui.entries[item.id] ?: return@items
                EntryResultRow(entry, ui, onClick = { onOpen(item.id) })
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** Подпись типа в строке результата. */
private fun typeLabel(e: EntryEntity): String = when (e.entryType) {
    EntryType.ONE_TIME -> "задача"
    EntryType.RECURRING -> "повтор"
    EntryType.BIRTHDAY -> "🎂 день рождения"
    EntryType.HOLIDAY -> "🎉 праздник"
    EntryType.PLAN -> "план · " + PlanScope.describe(PlanScope.parse(e.planScope))
    EntryType.GIFT -> "🎁 подарок · " + GiftStatus.parse(e.giftStatus).title
    EntryType.HABIT -> "🔥 привычка"
}

@Composable
private fun EntryResultRow(e: EntryEntity, ui: PinUi, onClick: () -> Unit) {
    val done = e.entryStatus == TaskStatus.DONE
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            (if (e.entryPriority == Priority.HIGH && !done) "❗ " else "") + e.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        val meta = buildList {
            add(typeLabel(e))
            e.listId?.let { ui.listsById[it] }?.let { add("● " + it.name) }
            e.dueDay?.let { add(RuText.relativeDayWithDate(it, ui.today)) }
            e.timeMinutes?.let { add(RuText.time(it)) }
            if (e.rollovers > 0 && !done) add(RuText.rollovers(e.rollovers))
            if (done) e.completedDay?.let { add("выполнено ${RuText.relativeDay(it, ui.today)}") }
            e.tagList.forEach { add("#$it") }
        }
        Text(
            meta.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
