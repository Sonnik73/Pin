package com.sonnik.pin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate

private enum class RepeatKind { NONE, DAILY, EVERY_N, WEEKLY, MONTHLY_DAY, MONTHLY_LAST, YEARLY }

private val WEEK = listOf(
    DayOfWeek.MONDAY to "пн", DayOfWeek.TUESDAY to "вт", DayOfWeek.WEDNESDAY to "ср", DayOfWeek.THURSDAY to "чт",
    DayOfWeek.FRIDAY to "пт", DayOfWeek.SATURDAY to "сб", DayOfWeek.SUNDAY to "вс",
)

/** Выбор правила повтора. Число месяца и дата для ежегодного берутся из выбранного дня [day]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatDialog(current: RepeatRule?, day: Long, onPick: (RepeatRule?) -> Unit, onDismiss: () -> Unit) {
    val date = LocalDate.ofEpochDay(day)
    var kind by rememberSaveable {
        mutableStateOf(
            when (current) {
                null -> RepeatKind.NONE
                RepeatRule.Daily -> RepeatKind.DAILY
                is RepeatRule.EveryNDays -> RepeatKind.EVERY_N
                is RepeatRule.Weekly -> RepeatKind.WEEKLY
                is RepeatRule.Monthly -> if (current.lastDay) RepeatKind.MONTHLY_LAST else RepeatKind.MONTHLY_DAY
                is RepeatRule.Yearly -> RepeatKind.YEARLY
            },
        )
    }
    var everyN by rememberSaveable { mutableStateOf(((current as? RepeatRule.EveryNDays)?.n ?: 2).toString()) }
    var weekMask by rememberSaveable {
        mutableIntStateOf(
            ((current as? RepeatRule.Weekly)?.days ?: setOf(date.dayOfWeek)).fold(0) { acc, d -> acc or (1 shl d.ordinal) },
        )
    }
    val monthDay = (current as? RepeatRule.Monthly)?.dayOfMonth ?: date.dayOfMonth

    fun build(): RepeatRule? = when (kind) {
        RepeatKind.NONE -> null
        RepeatKind.DAILY -> RepeatRule.Daily
        RepeatKind.EVERY_N -> everyN.toIntOrNull()?.takeIf { it >= 1 }?.let { if (it == 1) RepeatRule.Daily else RepeatRule.EveryNDays(it) }
        RepeatKind.WEEKLY -> DayOfWeek.entries.filter { weekMask and (1 shl it.ordinal) != 0 }.toSet().takeIf { it.isNotEmpty() }
            ?.let { RepeatRule.Weekly(it) }
        RepeatKind.MONTHLY_DAY -> RepeatRule.Monthly(monthDay)
        RepeatKind.MONTHLY_LAST -> RepeatRule.Monthly(null)
        RepeatKind.YEARLY -> RepeatRule.Yearly(date.monthValue, date.dayOfMonth)
    }

    val valid = kind == RepeatKind.NONE || build() != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Повтор") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Option("Не повторять", kind == RepeatKind.NONE) { kind = RepeatKind.NONE }
                Option("Каждый день", kind == RepeatKind.DAILY) { kind = RepeatKind.DAILY }
                Option("Каждые N дней", kind == RepeatKind.EVERY_N) { kind = RepeatKind.EVERY_N }
                if (kind == RepeatKind.EVERY_N) {
                    OutlinedTextField(
                        value = everyN,
                        onValueChange = { v -> everyN = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Дней") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(start = 48.dp).width(120.dp),
                    )
                }
                Option("По дням недели", kind == RepeatKind.WEEKLY) { kind = RepeatKind.WEEKLY }
                if (kind == RepeatKind.WEEKLY) {
                    FlowRow(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEK.forEach { (d, label) ->
                            val bit = 1 shl d.ordinal
                            FilterChip(
                                selected = weekMask and bit != 0,
                                onClick = { weekMask = weekMask xor bit },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Option("Каждый месяц, $monthDay-го числа", kind == RepeatKind.MONTHLY_DAY) { kind = RepeatKind.MONTHLY_DAY }
                Option("В последний день месяца", kind == RepeatKind.MONTHLY_LAST) { kind = RepeatKind.MONTHLY_LAST }
                Option(
                    "Каждый год, " + RepeatRule.describe(RepeatRule.Yearly(date.monthValue, date.dayOfMonth)).removePrefix("ежегодно "),
                    kind == RepeatKind.YEARLY,
                ) { kind = RepeatKind.YEARLY }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(build()) }, enabled = valid) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
