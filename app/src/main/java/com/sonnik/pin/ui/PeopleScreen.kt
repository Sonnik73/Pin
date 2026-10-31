package com.sonnik.pin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText

/** День рождения человека из записей. */
private fun PinUi.birthdayOf(personId: String): EntryEntity? =
    entries.values.firstOrNull { it.entryType == EntryType.BIRTHDAY && it.personId == personId }

private fun PinUi.giftsOf(personId: String): List<EntryEntity> =
    entries.values.filter { it.entryType == EntryType.GIFT && it.personId == personId }.sortedBy { GiftStatus.parse(it.giftStatus).ordinal }

/** «5 марта, через 124 дня · исполнится 31». */
private fun birthdayLine(birthday: EntryEntity, today: Long): String {
    val rule = birthday.rule as? RepeatRule.Yearly ?: return ""
    val day = birthday.dueDay ?: return RuText.dayMonth(rule.day, rule.month)
    val left = day - today
    val whenText = when (left) {
        0L -> "сегодня!"
        1L -> "завтра"
        else -> "через $left ${RuText.plural(left, "день", "дня", "дней")}"
    }
    val age = Occasions.ageOn(birthday.birthYear, day)?.let { " · исполнится $it" } ?: ""
    return "${RuText.dayMonth(rule.day, rule.month)}, $whenText$age"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(ui: PinUi, onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val people = ui.people.sortedBy { ui.birthdayOf(it.id)?.dueDay ?: Long.MAX_VALUE }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Люди и подарки", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Человек") })
        },
    ) { padding ->
        if (people.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Добавьте жену, друзей и родных — Pin напомнит о днях рождения и подскажет купить подарок заранее.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(people, key = { it.id }) { person ->
                val birthday = ui.birthdayOf(person.id)
                val gifts = ui.giftsOf(person.id)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(person.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(person.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(person.name, style = MaterialTheme.typography.bodyLarge)
                        val lines = buildList {
                            add(Relation.parse(person.relation).title)
                            birthday?.let { add("🎂 " + birthdayLine(it, ui.today)) }
                            val ideas = gifts.count { GiftStatus.parse(it.giftStatus) == GiftStatus.IDEA }
                            val bought = gifts.count { GiftStatus.parse(it.giftStatus) == GiftStatus.BOUGHT }
                            if (ideas + bought > 0) add("🎁 идей: $ideas, куплено: $bought")
                        }
                        Text(lines.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Карточка человека: имя, кто это, день рождения и подарки. [personId] = null — новый человек. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonScreen(
    ui: PinUi,
    personId: String?,
    onSave: (name: String, relation: Relation, note: String, birthday: PinRepository.Birthday?, remindDays: List<Int>) -> Unit,
    onDelete: () -> Unit,
    onSaveGift: (String?, PinRepository.GiftDraft) -> Unit,
    onGiftStatus: (String, GiftStatus) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onClose: () -> Unit,
) {
    val person: PersonEntity? = personId?.let { id -> ui.people.firstOrNull { it.id == id } }
    val birthday = person?.let { ui.birthdayOf(it.id) }
    val initialDate = (birthday?.rule as? RepeatRule.Yearly)?.let { RuText.formatDayMonth(it.day, it.month, birthday?.birthYear) } ?: ""
    var name by rememberSaveable { mutableStateOf(person?.name ?: "") }
    var relation by rememberSaveable { mutableStateOf(Relation.parse(person?.relation ?: Relation.FRIEND.name)) }
    var note by rememberSaveable { mutableStateOf(person?.note ?: "") }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var remind by rememberSaveable { mutableStateOf(Occasions.formatRemindDays(Occasions.parseRemindDays(birthday?.remindDays))) }
    var giftEditor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val parsedDate = RuText.parseDayMonth(date)
    val dateOk = date.isBlank() || parsedDate != null
    val canSave = name.isNotBlank() && dateOk

    fun save() {
        if (!canSave) return
        onSave(
            name,
            relation,
            note,
            parsedDate?.let { PinRepository.Birthday(it.month, it.day, it.year) },
            Occasions.parseRemindDays(remind),
        )
        onClose()
    }

    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (person == null) "Новый человек" else person.name) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (person != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                    TextButton(onClick = ::save, enabled = canSave) { Text("Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Relation.entries.forEach { r ->
                    FilterChip(selected = relation == r, onClick = { relation = r }, label = { Text(r.title) })
                }
            }
            OutlinedTextField(
                value = date,
                onValueChange = { v -> date = v.filter { it.isDigit() || it == '.' }.take(10) },
                label = { Text("День рождения") },
                placeholder = { Text("ДД.ММ или ДД.ММ.ГГГГ") },
                supportingText = {
                    Text(
                        when {
                            !dateOk -> "Не похоже на дату"
                            parsedDate?.year == null && parsedDate != null -> "Год можно не указывать — тогда без возраста"
                            else -> "Год — чтобы видеть возраст"
                        },
                    )
                },
                isError = !dateOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (parsedDate != null) {
                OutlinedTextField(
                    value = remind,
                    onValueChange = { v -> remind = v.filter { it.isDigit() || it == ',' || it == ' ' } },
                    label = { Text("Напомнить за, дней") },
                    supportingText = { Text("Через запятую; в сам день появится задача «Поздравить»") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Заметка") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            if (person != null) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Подарки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { giftEditor = "" }) { Text("+ Идея") }
                }
                val gifts = ui.giftsOf(person.id)
                if (gifts.isEmpty()) {
                    Text("Идей пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                gifts.forEach { gift -> GiftRow(gift, ui, onClick = { giftEditor = gift.id }, onStatus = { onGiftStatus(gift.id, it) }) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    val editing = giftEditor
    if (editing != null && person != null) {
        GiftDialog(
            ui = ui,
            person = person,
            gift = if (editing.isEmpty()) null else ui.entries[editing],
            onSave = { onSaveGift(if (editing.isEmpty()) null else editing, it); giftEditor = null },
            onDelete = { onDeleteEntry(editing); giftEditor = null },
            onDismiss = { giftEditor = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${person?.name}»?") },
            text = { Text("Вместе с днём рождения и подарками.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(); onClose() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

/** Название повода для подарка. */
private fun occasionTitle(ui: PinUi, occasionId: String?): String? {
    val e = occasionId?.let { ui.entries[it] } ?: return null
    return if (e.entryType == EntryType.BIRTHDAY) "день рождения" else e.title
}

@Composable
private fun GiftRow(gift: EntryEntity, ui: PinUi, onClick: () -> Unit, onStatus: (GiftStatus) -> Unit) {
    val status = GiftStatus.parse(gift.giftStatus)
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(gift.title)
            val meta = listOfNotNull(gift.price, occasionTitle(ui, gift.occasionId)?.let { "к: $it" })
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            gift.link?.let { link ->
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable { runCatching { uri.openUri(link) } },
                )
            }
        }
        // Нажатие двигает статус дальше: идея → куплен → подарен → снова идея.
        AssistChip(
            onClick = { onStatus(GiftStatus.entries[(status.ordinal + 1) % GiftStatus.entries.size]) },
            label = { Text(status.title) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GiftDialog(
    ui: PinUi,
    person: PersonEntity,
    gift: EntryEntity?,
    onSave: (PinRepository.GiftDraft) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(gift?.title ?: "") }
    var price by rememberSaveable { mutableStateOf(gift?.price ?: "") }
    var link by rememberSaveable { mutableStateOf(gift?.link ?: "") }
    val birthday = ui.birthdayOf(person.id)
    var occasion by rememberSaveable { mutableStateOf(gift?.occasionId ?: birthday?.id) }
    val occasions = buildList {
        birthday?.let { add(it.id to "День рождения") }
        ui.entries.values.filter { it.entryType == EntryType.HOLIDAY && !it.disabled }
            .sortedBy { it.dueDay }
            .forEach { add(it.id to it.title) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (gift == null) "Идея подарка" else "Подарок") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Что подарить") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Цена") }, singleLine = true)
                OutlinedTextField(link, { link = it }, label = { Text("Ссылка") }, singleLine = true)
                Text("Повод", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = occasion == null, onClick = { occasion = null }, label = { Text("Без повода") })
                    occasions.forEach { (id, label) ->
                        FilterChip(selected = occasion == id, onClick = { occasion = id }, label = { Text(label) })
                    }
                }
                if (gift != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        PinRepository.GiftDraft(
                            title = title,
                            personId = person.id,
                            occasionId = occasion,
                            price = price,
                            link = link,
                            status = GiftStatus.parse(gift?.giftStatus),
                            body = gift?.body ?: "",
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
