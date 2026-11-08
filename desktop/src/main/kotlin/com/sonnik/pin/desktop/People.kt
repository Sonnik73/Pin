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

// ---------- Люди ----------

/** Человек для списка: ближайший день рождения и сколько до него. */
internal data class PersonRow(val person: PersonEntity, val birthday: Edits.Birthday?, val nextDay: Long?)

internal fun personRows(data: PinData, today: Long): List<PersonRow> =
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
    var importing by remember { mutableStateOf<com.sonnik.pin.engine.BirthdayText.Result?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val rows = remember(data, today) { personRows(data, today) }
    val shown = rows.filter { query.isBlank() || it.person.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            PaneHeader("Люди", "Дни рождения — ближайшие сверху", "Человек") { creating = true }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                OutlinedButton(onClick = {
                    val file = pickFile("Добавить людей из файла (.zip, .vcf, .txt)") ?: return@OutlinedButton
                    val parsed = runCatching { com.sonnik.pin.engine.PeopleFile.parse(file.readBytes(), LocalDate.ofEpochDay(today).year) }.getOrNull()
                    if (parsed == null || parsed.people.isEmpty()) {
                        notice = "В файле не нашлось людей. Подойдёт .zip или .vcf из «Поделиться людьми», .vcf из контактов " +
                            "или .txt, где на каждой строке имя и дата: «Аня 10.11.1997»."
                    } else {
                        notice = null
                        importing = parsed
                    }
                }) { Text("Импорт из файла…") }
                if (rows.isNotEmpty()) OutlinedButton(onClick = { sharing = true }) { Text("Поделиться…") }
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(bottom = 8.dp)) }
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
                                val gifts = data.giftsOf(row.person.id)
                                val ideas = gifts.count { GiftStatus.parse(it.giftStatus) == GiftStatus.IDEA }
                                val bought = gifts.count { GiftStatus.parse(it.giftStatus) == GiftStatus.BOUGHT }
                                if (ideas > 0) add("🎁 идей: $ideas")
                                if (bought > 0) add("куплено: $bought")
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
    val incoming = importing
    if (store != null && incoming != null) {
        EditDialog("Импорт людей", 680, onClose = { importing = null }) {
            ImportPeopleContent(data, incoming, onImport = { chosen ->
                store.commit { Edits.importPeople(it, chosen, today, System.currentTimeMillis()).first }
                val n = com.sonnik.pin.engine.PeopleImport.plan(Edits.peopleBirthdays(data), chosen).count { it.second != com.sonnik.pin.engine.ImportAction.SKIP }
                notice = "Добавлено или дополнено: $n" + (incoming.skipped.takeIf { it.isNotEmpty() }?.let { " · строки без даты пропущены: ${it.joinToString(", ")}" } ?: "")
                importing = null
            }, onClose = { importing = null })
        }
    }
    if (sharing) {
        EditDialog("Поделиться людьми", 680, onClose = { sharing = false }) {
            SharePeopleContent(data, onSave = { ids ->
                val people = Edits.sharedPeople(data, ids)
                val target = saveFile("Сохранить людей", com.sonnik.pin.engine.PeopleFile.fileName(people))
                if (target != null) {
                    runCatching { target.writeBytes(com.sonnik.pin.engine.PeopleFile.zip(people)) }
                        .onSuccess { notice = "Сохранено: ${target.path}. Этот .zip можно переслать в Telegram — на телефоне откройте его в Pin." }
                        .onFailure { notice = "Не удалось сохранить: ${it.message}" }
                    sharing = false
                }
            }, onClose = { sharing = false })
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
                onSaveGift = { gid, gift -> store.commit { Edits.saveGift(it, gid, gift, System.currentTimeMillis()).first } },
                onGiftStatus = { gid, status -> store.commit { Edits.setGiftStatus(it, gid, status, System.currentTimeMillis()) } },
                onDeleteGift = { gid -> store.commit { Edits.delete(it, gid, System.currentTimeMillis()) } },
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
    onSaveGift: (String?, Edits.Gift) -> Unit = { _, _ -> },
    onGiftStatus: (String, GiftStatus) -> Unit = { _, _ -> },
    onDeleteGift: (String) -> Unit = {},
) {
    val person = id?.let { pid -> data.people.firstOrNull { it.id == pid } }
    // Правка подарка: null — закрыта, "" — новая идея.
    var giftEditing by remember(id) { mutableStateOf<String?>(null) }
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
        if (person != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Подарки")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { giftEditing = "" }) { Text("+ Идея") }
            }
            val gifts = data.giftsOf(person.id)
            if (gifts.isEmpty()) Hint("Идеи подарков: что подарить, цена, ссылка. Щелчок по статусу: идея → куплен → подарен.")
            gifts.forEach { g ->
                val status = GiftStatus.parse(g.giftStatus)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { giftEditing = g.id }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.title, textDecoration = if (status == GiftStatus.GIVEN) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        val meta = listOfNotNull(g.price, occasionTitle(data, g.occasionId)?.let { "к: $it" }, g.link)
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilterChip(
                        selected = status != GiftStatus.IDEA,
                        onClick = { onGiftStatus(g.id, GiftStatus.entries[(status.ordinal + 1) % GiftStatus.entries.size]) },
                        label = { Text(status.title) },
                    )
                }
            }
        }
    }
    val giftId = giftEditing
    if (person != null && giftId != null) {
        EditDialog(if (giftId.isEmpty()) "Идея подарка" else "Подарок", 560, onClose = { giftEditing = null }) {
            GiftContent(
                data, person, giftId.ifEmpty { null },
                onSave = { gift -> onSaveGift(giftId.ifEmpty { null }, gift); giftEditing = null },
                onDelete = { onDeleteGift(giftId); giftEditing = null },
                onClose = { giftEditing = null },
            )
        }
    }
}

internal fun PinData.giftsOf(personId: String): List<EntryEntity> =
    entries.filter { !it.deleted && it.entryType == EntryType.GIFT && it.personId == personId }.sortedBy { GiftStatus.parse(it.giftStatus).ordinal }

internal fun occasionTitle(data: PinData, occasionId: String?): String? {
    val e = occasionId?.let(data.byId::get) ?: return null
    return if (e.entryType == EntryType.BIRTHDAY) "день рождения" else e.title
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GiftContent(data: PinData, person: PersonEntity, id: String?, onSave: (Edits.Gift) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val gift = id?.let(data.byId::get)
    val birthday = data.entries.firstOrNull { !it.deleted && it.entryType == EntryType.BIRTHDAY && it.personId == person.id }
    var title by remember(id) { mutableStateOf(gift?.title ?: "") }
    var price by remember(id) { mutableStateOf(gift?.price ?: "") }
    var link by remember(id) { mutableStateOf(gift?.link ?: "") }
    var occasion by remember(id) { mutableStateOf(gift?.occasionId ?: birthday?.id) }
    val occasions = buildList {
        birthday?.let { add(it.id to "День рождения") }
        data.entries.filter { it.entryType == EntryType.HOLIDAY && !it.disabled && !it.deleted }.sortedBy { it.dueDay }.forEach { add(it.id to it.title) }
    }
    DialogBody(
        buttons = {
            if (gift != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(
                enabled = title.isNotBlank(),
                onClick = { onSave(Edits.Gift(title, person.id, occasion, price, link, GiftStatus.parse(gift?.giftStatus), gift?.body ?: "")) },
            ) { Text("Сохранить") }
        },
    ) {
        Hint("Для: ${person.name}")
        OutlinedTextField(title, { title = it }, label = { Text("Что подарить") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("Цена") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(link, { link = it }, label = { Text("Ссылка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Повод")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = occasion == null, onClick = { occasion = null }, label = { Text("Без повода") })
            occasions.forEach { (oid, label) -> FilterChip(selected = occasion == oid, onClick = { occasion = oid }, label = { Text(label) }) }
        }
    }
}
