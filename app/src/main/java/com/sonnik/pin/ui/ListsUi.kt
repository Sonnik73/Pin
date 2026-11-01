package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.RuText

/** Цвет списка под текущую тему (светлую или тёмную). */
@Composable
fun ListColor.color(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return Color(if (dark) this.dark else light)
}

@Composable
fun ListEntity.color(): Color = ListColor.parse(color).color()

/** Цветная точка списка. */
@Composable
fun ListDot(list: ListEntity, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(list.color(), CircleShape))
}

/** Выбор списка в редакторе: «Без списка» и все списки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListPicker(lists: List<ListEntity>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (lists.isEmpty() || !com.sonnik.pin.engine.Feature.LISTS.on()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Список", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Без списка") })
            lists.forEach { l ->
                FilterChip(
                    selected = selected == l.id,
                    onClick = { onSelect(l.id) },
                    label = { Text(l.name) },
                    leadingIcon = { ListDot(l) },
                )
            }
        }
    }
}

/** Фильтр по спискам: «Все» и списки; пусто — нет строки. */
@Composable
fun ListFilterRow(lists: List<ListEntity>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (lists.isEmpty() || !com.sonnik.pin.engine.Feature.LISTS.on()) return
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Все") })
        lists.forEach { l ->
            FilterChip(
                selected = selected == l.id,
                onClick = { onSelect(if (selected == l.id) null else l.id) },
                label = { Text(l.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { ListDot(l) },
            )
        }
    }
}

/** Настройки → «Списки»: добавить, переименовать, перекрасить, удалить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    lists: List<ListEntity>,
    counts: Map<String, Int>,
    onSave: (id: String?, name: String, color: ListColor) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf<ListEntity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Списки") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Новый список") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lists.isEmpty()) {
                item {
                    Text(
                        "Списков пока нет. Списки собирают задачи по проектам: «Дом», «Работа», «Ремонт». " +
                            "Создайте список кнопкой «+» или напишите в быстром добавлении «@дом».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(lists, key = { it.id }) { l ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { editing = l },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ListDot(l, 16.dp)
                        Text(l.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            (counts[l.id] ?: 0).let { "$it " + RuText.plural(it, "задача", "задачи", "задач") },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        ListDialog(
            list = editing,
            suggested = ListColor.next(lists.map { it.color }),
            onSave = { name, color -> onSave(editing?.id, name, color); creating = false; editing = null },
            onDelete = editing?.let { l -> { onDelete(l.id); editing = null } },
            onDismiss = { creating = false; editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListDialog(
    list: ListEntity?,
    suggested: ListColor,
    onSave: (String, ListColor) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(list?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(list?.let { ListColor.parse(it.color) } ?: suggested) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить список «${list?.name}»?") },
            text = { Text("Задачи останутся, просто без списка.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (list == null) "Новый список" else "Список") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListColor.entries.forEach { c ->
                        val chosen = c == color
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(c.color(), CircleShape)
                                .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
                Text(color.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
