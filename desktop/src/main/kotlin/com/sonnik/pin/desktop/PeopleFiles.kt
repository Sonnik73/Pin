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

/** Выбрать файл (окно Windows). */
internal fun pickFile(title: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD).apply { isVisible = true }
    return dialog.file?.let { java.io.File(dialog.directory, it) }
}

/** Куда сохранить файл (окно Windows); расширение .zip дописывается, если его нет. */
internal fun saveFile(title: String, name: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE).apply {
        file = name
        listOf(java.io.File(System.getProperty("user.home"), "Downloads"), java.io.File(System.getProperty("user.home")))
            .firstOrNull { it.isDirectory }?.let { directory = it.path }
        isVisible = true
    }
    var chosen = dialog.file ?: return null
    if (!chosen.endsWith(".zip", ignoreCase = true)) chosen += ".zip"
    return java.io.File(dialog.directory, chosen)
}

internal fun personLine(p: com.sonnik.pin.engine.SharedPerson): String = listOfNotNull(
    p.month?.let { m -> p.day?.let { d -> "🎂 " + RuText.dayMonth(d, m) + (p.year?.let { " $it" } ?: "") } },
    p.relation.takeIf { it.isNotBlank() }?.let { Relation.title(it) },
    p.note.takeIf { it.isNotBlank() },
).joinToString(" · ")

/** Предпросмотр импорта: кто новый, кому допишется день рождения, кто уже есть; галочками — кого добавить. */
@Composable
internal fun ImportPeopleContent(
    data: PinData,
    parsed: com.sonnik.pin.engine.BirthdayText.Result,
    onImport: (List<com.sonnik.pin.engine.SharedPerson>) -> Unit,
    onClose: () -> Unit,
) {
    val plan = remember(parsed) { com.sonnik.pin.engine.PeopleImport.plan(Edits.peopleBirthdays(data), parsed.people) }
    var chosen by remember(parsed) { mutableStateOf(plan.indices.filter { plan[it].second != com.sonnik.pin.engine.ImportAction.SKIP }.toSet()) }
    DialogBody(
        buttons = {
            TextButton(onClick = { chosen = plan.indices.filter { plan[it].second != com.sonnik.pin.engine.ImportAction.SKIP }.toSet() }) { Text("Выбрать всех") }
            TextButton(onClick = { chosen = emptySet() }) { Text("Снять все") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(enabled = chosen.isNotEmpty(), onClick = { onImport(chosen.sorted().map { plan[it].first }) }) { Text("Добавить ${chosen.size}") }
        },
    ) {
        Hint("В файле: ${plan.size}. Свои данные не перезаписываются: у тех, кто уже есть, добавится только день рождения, если его не было.")
        if (parsed.skipped.isNotEmpty()) Hint("Строки без даты пропущены: ${parsed.skipped.joinToString(", ")}")
        plan.forEachIndexed { i, (p, action) ->
            val skip = action == com.sonnik.pin.engine.ImportAction.SKIP
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = i in chosen,
                    enabled = !skip,
                    onCheckedChange = { on -> chosen = if (on) chosen + i else chosen - i },
                )
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = if (skip) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    val line = personLine(p)
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    action.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (skip) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** «Поделиться людьми»: выбрать, кого отправить, и сохранить .zip (его можно переслать в Telegram). */
@Composable
internal fun SharePeopleContent(data: PinData, onSave: (Set<String>) -> Unit, onClose: () -> Unit) {
    val people = data.people.filter { !it.deleted }.sortedBy { it.name }
    var chosen by remember { mutableStateOf(people.map { it.id }.toSet()) }
    var query by remember { mutableStateOf("") }
    DialogBody(
        buttons = {
            TextButton(onClick = { chosen = people.map { it.id }.toSet() }) { Text("Всех") }
            TextButton(onClick = { chosen = emptySet() }) { Text("Никого") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            Button(enabled = chosen.isNotEmpty(), onClick = { onSave(chosen) }) { Text("Сохранить .zip (${chosen.size})") }
        },
    ) {
        Hint("Уйдут имя, категория, заметка и день рождения. Задачи и подарки не передаются.")
        OutlinedTextField(query, { query = it }, placeholder = { Text("Поиск по имени") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        people.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = p.id in chosen, onCheckedChange = { on -> chosen = if (on) chosen + p.id else chosen - p.id })
                Text(p.name, modifier = Modifier.weight(1f))
                Edits.birthdayOf(data, p.id)?.let { b -> Text("🎂 " + RuText.dayMonth(b.day, b.month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
