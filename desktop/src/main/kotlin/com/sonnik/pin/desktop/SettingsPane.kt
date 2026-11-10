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

private val RU = Locale.forLanguageTag("ru")
// ---------- Настройки ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPane(
    path: String?,
    onChoose: () -> Unit,
    onCreate: () -> Unit = {},
    modified: Long? = null,
    data: PinData? = null,
    store: PinStore? = null,
    today: Long = 0,
    offlineSince: Long? = null,
) {
    var listEditing by remember { mutableStateOf<String?>(null) }
    var holidays by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PaneHeader("Настройки", "Pin $APP_VERSION для Windows", null) {}
            FieldLabel("Файл")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(path ?: "Не выбран", style = MaterialTheme.typography.bodyLarge)
                        modified?.takeIf { it > 0 }?.let {
                            Hint("Изменён " + java.time.format.DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm").format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())))
                        }
                        Hint("Pin.zip — лучше в папке Google Диска, тогда тот же файл подключается на телефоне. Программа перечитывает его сама.")
                        Hint("Копия всех данных хранится и на этом компьютере (" + LocalCopy.defaultDir().path + "): если общий файл недоступен, задачи видны и правятся, а правки уедут в файл, когда он вернётся.")
                        offlineSince?.let {
                            Text(
                                "Нет связи с файлом с " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())) +
                                    " — работаю с копией на этом компьютере.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onChoose) { Text("Открыть…") }
                        TextButton(onClick = onCreate) { Text("Новый файл…") }
                    }
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

            if (data != null) {
                FieldLabel("Списки")
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val lists = data.lists.filter { !it.deleted }.sortedBy { it.sortOrder }
                        if (lists.isEmpty()) Hint("Списков пока нет. Их можно создать здесь или строкой «@дом» в быстром добавлении.")
                        lists.forEach { l ->
                            val count = data.entries.count { !it.deleted && it.listId == l.id && it.entryStatus == com.sonnik.pin.engine.TaskStatus.OPEN }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { listEditing = l.id }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(listColor(l.color)))
                                Spacer(Modifier.width(12.dp))
                                Text(l.name, modifier = Modifier.weight(1f))
                                Text("открытых: $count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { listEditing = "" }) { Text("+ Список") }
                    }
                }
                FieldLabel("Праздники и журнал изменений")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { holidays = true }) { Text("Праздники…") }
                    OutlinedButton(onClick = { changelog = true }) { Text("Что нового…") }
                }
            }

            FieldLabel("Новый день начинается в")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 3, 4, 5, 6).forEach { h ->
                    FilterChip(
                        selected = DesktopPrefs.boundaryMinutes == h * 60,
                        onClick = { DesktopPrefs.setBoundary(h * 60) },
                        label = { Text("%d:00".format(h)) },
                    )
                }
            }
            Hint("Поставьте так же, как на телефоне (Настройки → «Граница дня»): до этого времени ещё «вчера», ночью ничего не переносится.")

            FieldLabel("Напоминания и запуск")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SwitchLine("Напоминания Windows", "Время задач, привычки, утренняя сводка с днями рождения — пока Pin запущен (можно в трее)", DesktopReminders.enabled) {
                        DesktopReminders.switch(it)
                    }
                    var morning by remember { mutableStateOf(RuText.time(DesktopReminders.morningMinutes)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Утренняя сводка в", style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            morning,
                            { v -> morning = v; DateText.parseTime(v)?.let(DesktopReminders::setMorning) },
                            singleLine = true,
                            isError = DateText.parseTime(morning) == null,
                            modifier = Modifier.width(110.dp),
                        )
                    }
                    SwitchLine("Закрывать в трей", "Крестик прячет окно, Pin остаётся в области уведомлений; выйти — правой кнопкой по значку", TrayMode.onClose) {
                        TrayMode.set(it)
                    }
                    SwitchLine(
                        "Запускать при входе в Windows",
                        if (Autostart.exe != null) "Сразу в трей, без окна — чтобы напоминания приходили с утра" else "Доступно в установленной программе",
                        Autostart.enabled,
                        enabled = Autostart.exe != null,
                    ) { Autostart.set(it) }
                }
            }
            FieldLabel("Обновления")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SwitchLine("Проверять обновления", "Не чаще раза в 6 часов спросить GitHub, нет ли новой версии", UpdateCheck.enabled) { UpdateCheck.switch(it) }
                    var checking by remember { mutableStateOf<String?>(null) }
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            checking = "Проверяю…"
                            scope.launch {
                                val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpdateCheck.check(force = true) }
                                checking = if (found != null) "Есть новая версия ${found.version} — плашка вверху окна" else "У вас последняя версия ($APP_VERSION)"
                            }
                        }) { Text("Проверить сейчас") }
                        checking?.let { Hint(it) }
                    }
                }
            }

            FieldLabel("Функции")
            Hint("Выключенное только прячется: данные остаются, включите — всё на месте. Хранится на этом компьютере.")
            Card {
                Column {
                    DESKTOP_FEATURES.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = MaterialTheme.typography.bodyLarge)
                                Text(f.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = f.on(), onCheckedChange = { DesktopFeatures.set(f, it) })
                        }
                    }
                }
            }
        }
    }

    if (store != null && data != null) {
        listEditing?.let { lid ->
            EditDialog(if (lid.isEmpty()) "Новый список" else "Список", 420, onClose = { listEditing = null }) {
                ListContent(
                    data, lid.ifEmpty { null },
                    onSave = { name, color -> store.commit { Edits.saveList(it, lid.ifEmpty { null }, name, color, System.currentTimeMillis()).first }; listEditing = null },
                    onDelete = { store.commit { Edits.deleteList(it, lid, System.currentTimeMillis()) }; listEditing = null },
                    onClose = { listEditing = null },
                )
            }
        }
        if (holidays) {
            EditDialog("Праздники", 760, onClose = { holidays = false }) { HolidaysContent(data, today, store) }
        }
    }
    if (changelog) {
        EditDialog("Что нового", 760, onClose = { changelog = false }) { ChangelogContent() }
    }
}

@Composable
internal fun SwitchLine(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Функции, которые есть в программе для Windows (свайпы, голос и сетевой Диск — только на телефоне). */
internal val DESKTOP_FEATURES = listOf(
    Feature.QUICK_ADD, Feature.REVIEW, Feature.UPCOMING, Feature.HABITS, Feature.LISTS,
    Feature.MATRIX, Feature.FOCUS, Feature.PEOPLE, Feature.PLANS, Feature.MOMENTS, Feature.STATS,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListContent(data: PinData, id: String?, onSave: (String, ListColor) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val list = id?.let { lid -> data.lists.firstOrNull { it.id == lid } }
    var name by remember(id) { mutableStateOf(list?.name ?: "") }
    var color by remember(id) { mutableStateOf(list?.let { ListColor.parse(it.color) } ?: ListColor.next(data.lists.filter { !it.deleted }.map { it.color })) }
    DialogBody(
        buttons = {
            if (list != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(enabled = name.isNotBlank(), onClick = { onSave(name, color) }) { Text("Сохранить") }
        },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Цвет")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListColor.entries.forEach { c ->
                FilterChip(
                    selected = color == c,
                    onClick = { color = c },
                    label = { Text(c.title) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(listColor(c.name))) },
                )
            }
        }
        if (list != null) Hint("Удаление списка не трогает задачи — они просто останутся без списка.")
    }
}

@Composable
internal fun HolidaysContent(data: PinData, today: Long, store: PinStore?) {
    val all = data.entries.filter { it.entryType == EntryType.HOLIDAY && !it.deleted }
        .sortedWith(compareBy({ (it.rule as? RepeatRule.Yearly)?.month }, { (it.rule as? RepeatRule.Yearly)?.day }))
    val missing = Holidays.RU.count { h -> data.byId[h.entryId] == null }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    val parsed = if (date.isBlank()) null else parseBirthday(date)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("Праздники видны в календаре и на «Сегодня». Выключенный праздник прячется и на телефоне.")
        if (missing > 0) {
            OutlinedButton(onClick = { store?.commit { Edits.seedHolidays(it, today, System.currentTimeMillis()) } }) {
                Text("Добавить праздники России ($missing)")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(title, { title = it }, label = { Text("Свой праздник") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(date, { date = it }, label = { Text("Дата") }, placeholder = { Text("14.06") }, singleLine = true, isError = date.isNotBlank() && parsed == null, modifier = Modifier.width(140.dp))
            Button(
                enabled = title.isNotBlank() && parsed != null,
                onClick = {
                    val p = parsed ?: return@Button
                    store?.commit { Edits.saveHoliday(it, null, title, p.month, p.day, today, System.currentTimeMillis()) }
                    title = ""
                    date = ""
                },
            ) { Text("Добавить") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(all, key = { it.id }) { h ->
                val builtIn = Holidays.byEntryId(h.id)
                val rule = h.rule as? RepeatRule.Yearly
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.title)
                        Text(
                            (rule?.let { RuText.dayMonth(it.day, it.month) } ?: "") + " · " + (builtIn?.let { it.kind.emoji + " " + it.kind.title } ?: "свой"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (builtIn == null) {
                        TextButton(onClick = { store?.commit { Edits.delete(it, h.id, System.currentTimeMillis()) } }) { Text("Удалить") }
                    }
                    Switch(checked = !h.disabled, onCheckedChange = { on -> store?.commit { Edits.setDisabled(it, h.id, !on, System.currentTimeMillis()) } })
                }
            }
        }
    }
}

/** Журнал изменений — тот же файл, что в «Истории версий» на телефоне. */
@Composable
internal fun ChangelogContent() {
    val entries = remember {
        val text = Thread.currentThread().contextClassLoader.getResourceAsStream("changelog_ru.md")?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        com.sonnik.pin.engine.Changelog.parse(text)
    }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (entries.isEmpty()) item { Hint("Журнал изменений не найден.") }
        items(entries, key = { it.version }) { e ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pin ${e.version}", style = MaterialTheme.typography.titleMedium)
                e.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
