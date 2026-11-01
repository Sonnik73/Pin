package com.sonnik.pin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.ImportAction
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.SharedPerson

/** Строка для выбора: галочка, имя и подпись. */
private data class PickRow(val key: String, val title: String, val subtitle: String, val enabled: Boolean = true)

/** Общий вид окна с галочками: заголовок, пояснение, «Выбрать всех», список и кнопки. */
@Composable
private fun PickPanel(
    title: String,
    hint: String,
    rows: List<PickRow>,
    selected: Set<String>,
    onSelected: (Set<String>) -> Unit,
    action: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectable = rows.filter { it.enabled }.map { it.key }.toSet()
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
            )
            if (selectable.size > 1) {
                val all = selected.containsAll(selectable)
                TextButton(onClick = { onSelected(if (all) emptySet() else selectable) }, modifier = Modifier.padding(start = 12.dp)) {
                    Text(if (all) "Снять все" else "Выбрать всех")
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(rows, key = { it.key }) { r ->
                    val checked = r.key in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = r.enabled) { onSelected(if (checked) selected - r.key else selected + r.key) }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = r.enabled, modifier = Modifier.padding(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (r.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (r.subtitle.isNotEmpty()) {
                                Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = onAction, enabled = selected.isNotEmpty()) {
                    Text(if (selected.isEmpty()) action else "$action · ${selected.size}")
                }
            }
        }
    }
}

private fun birthdayText(month: Int?, day: Int?, year: Int?): String =
    if (month == null || day == null) "без дня рождения" else "🎂 " + RuText.dayMonth(day, month) + (year?.let { " $it" } ?: "")

/** Содержимое «Поделиться людьми»: выбрать, кого отправить (по умолчанию — никого, выбираете сами). */
@Composable
fun SharePeoplePanel(people: List<SharedPersonRow>, onShare: (List<String>) -> Unit, onDismiss: () -> Unit, initial: Set<String> = emptySet()) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    PickPanel(
        title = "Поделиться людьми",
        hint = "Отметьте, кого отправить. Уйдут имя, кто это, заметка и день рождения — подарки остаются у вас. " +
            "Файл .vcf откроется в Pin на другом телефоне.",
        rows = people.map { PickRow(it.id, it.name, it.subtitle) },
        selected = selected,
        onSelected = { selected = it },
        action = "Поделиться",
        onAction = { onShare(people.map { it.id }.filter { it in selected }) },
        onDismiss = onDismiss,
    )
}

/** Человек в списке «Поделиться». */
data class SharedPersonRow(val id: String, val name: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePeopleDialog(people: List<SharedPersonRow>, onShare: (List<String>) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) { SharePeoplePanel(people, onShare, onDismiss) }
}

/** Содержимое импорта: кого добавить из файла; кто уже есть — серым, без галочки. */
@Composable
fun ImportPeoplePanel(plan: List<Pair<SharedPerson, ImportAction>>, onImport: (List<SharedPerson>) -> Unit, onDismiss: () -> Unit) {
    val keyed = plan.mapIndexed { i, (p, a) -> "$i" to (p to a) }
    var selected by rememberSaveable { mutableStateOf(keyed.filter { it.second.second != ImportAction.SKIP }.map { it.first }.toSet()) }
    PickPanel(
        title = "Люди из файла",
        hint = "Отметьте, кого добавить. Тех, кто уже есть, Pin не трогает — только допишет день рождения, если его не было.",
        rows = keyed.map { (k, pa) ->
            val (p, a) = pa
            PickRow(k, p.name, birthdayText(p.month, p.day, p.year) + " · " + a.title, enabled = a != ImportAction.SKIP)
        },
        selected = selected,
        onSelected = { selected = it },
        action = "Добавить",
        onAction = { onImport(keyed.filter { it.first in selected }.map { it.second.first }) },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPeopleDialog(plan: List<Pair<SharedPerson, ImportAction>>, onImport: (List<SharedPerson>) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) { ImportPeoplePanel(plan, onImport, onDismiss) }
}

/** Подпись человека в списке «Поделиться»: кто это и день рождения. */
fun PinUi.shareRows(): List<SharedPersonRow> = people.sortedBy { it.name }.map { p ->
    val rule = birthdayOf(p.id)?.rule as? com.sonnik.pin.engine.RepeatRule.Yearly
    SharedPersonRow(
        p.id,
        p.name,
        com.sonnik.pin.engine.Relation.title(p.relation) + " · " + birthdayText(rule?.month, rule?.day, birthdayOf(p.id)?.birthYear),
    )
}
