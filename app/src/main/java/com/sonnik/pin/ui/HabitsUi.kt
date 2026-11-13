package com.sonnik.pin.ui

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import com.sonnik.pin.engine.RuText
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.HabitInfo
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.RepeatRule
import java.time.DayOfWeek
import kotlin.math.roundToInt

private val WEEK = listOf(
    DayOfWeek.MONDAY to "пн", DayOfWeek.TUESDAY to "вт", DayOfWeek.WEDNESDAY to "ср", DayOfWeek.THURSDAY to "чт",
    DayOfWeek.FRIDAY to "пт", DayOfWeek.SATURDAY to "сб", DayOfWeek.SUNDAY to "вс",
)

/** Строка привычки: кружок-отметка за сегодня, название, серия. */
@Composable
fun HabitRow(title: String, habit: HabitInfo, today: Long, onToggle: () -> Unit, onOpen: () -> Unit, card: Boolean = true) {
    val stats = remember(habit, today) { Habits.stats(habit, today) }
    val week = remember(habit, today) { Habits.week(habit, today) }
    val colors = MaterialTheme.colorScheme
    // Внутри другой карточки («Итоги») — без своей подложки.
    PinCard(
        if (card) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier,
        onClick = onOpen,
        color = if (card) colors.surfaceContainer else androidx.compose.ui.graphics.Color.Transparent,
    ) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (card) 12.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Кольцо — неделя (отмечено из запланированного), внутри — отметка за сегодня (зелёная: успех).
        val (doneFill, doneMark) = doneColors(colors.background.luminance() < 0.5f)
        ProgressRing(
            fraction = if (week.second == 0) 0f else week.first.toFloat() / week.second,
            size = 44.dp,
            stroke = 4.dp,
            color = colors.tertiary,
            track = colors.tertiary.copy(alpha = 0.15f),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (stats.doneToday) doneFill else colors.surface, CircleShape)
                    .border(2.dp, if (stats.scheduledToday || stats.doneToday) doneFill else colors.outlineVariant, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (stats.doneToday) Icon(Icons.Filled.Check, contentDescription = "Отмечено", tint = doneMark, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildList {
                    if (week.second > 0) add("неделя ${week.first}/${week.second}")
                    if (!stats.scheduledToday) add("сегодня не по плану")
                    stats.rate30?.let { add("${(it * 100).roundToInt()}% за 30 дней") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (stats.current > 0) {
            Text("🔥 ${stats.current}", style = MaterialTheme.typography.titleSmall, color = colors.tertiary)
        }
    }
    }
}

/** Карточка привычки: название, расписание, серии, тепловая карта года. [habitId] = null — новая. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitScreen(
    ui: PinUi,
    habitId: String?,
    onSave: (title: String, rule: RepeatRule?, remindAt: Int?) -> Unit,
    onToggle: (day: Long) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val entry = habitId?.let { ui.entries[it] }
    val habit = ui.habits.firstOrNull { it.id == habitId }
    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    val initialMask = (entry?.rule as? RepeatRule.Weekly)?.days?.fold(0) { acc, d -> acc or (1 shl d.ordinal) } ?: 0x7f
    var mask by rememberSaveable { mutableIntStateOf(initialMask) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var remindAt by rememberSaveable { mutableStateOf(entry?.timeMinutes) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val rule: RepeatRule? = if (mask == 0x7f || mask == 0) null else RepeatRule.Weekly(DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet())
    BackHandler(onBack = onClose)
    fun save() {
        if (title.isBlank()) return
        onSave(title, rule, remindAt)
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) "Новая привычка" else "Привычка") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (entry != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                    TextButton(onClick = ::save, enabled = title.isNotBlank()) { Text("Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Привычка") },
                placeholder = { Text("Зарядка, 2 литра воды, читать 20 минут") },
                singleLine = true,
                keyboardOptions = SENTENCES,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("По каким дням", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mask == 0x7f, onClick = { mask = 0x7f }, label = { Text("Каждый день") })
                WEEK.forEach { (d, label) ->
                    val bit = 1 shl d.ordinal
                    FilterChip(selected = mask != 0x7f && mask and bit != 0, onClick = {
                        mask = if (mask == 0x7f) bit else (mask xor bit).takeIf { it != 0 } ?: 0x7f
                    }, label = { Text(label) })
                }
            }
            Text("Напоминание", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = remindAt != null,
                    onClick = { pickTime = true },
                    label = { Text(remindAt?.let { "🔔 в ${RuText.time(it)}" } ?: "🔔 Выбрать время") },
                )
                if (remindAt != null) {
                    FilterChip(selected = false, onClick = { remindAt = null }, label = { Text("Без напоминания") })
                }
            }
            if (remindAt != null) {
                Text(
                    "Придёт в выбранные дни, если привычка ещё не отмечена. В уведомлении — кнопка «Отмечено».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (habit != null) {
                val stats = Habits.stats(habit, ui.today)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Серия", "🔥 ${stats.current}", Modifier.weight(1f))
                    StatTile("Лучшая", "${stats.best}", Modifier.weight(1f))
                    StatTile("30 дней", stats.rate30?.let { "${(it * 100).roundToInt()}%" } ?: "—", Modifier.weight(1f))
                }
                Text("Год", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Heatmap(habit, ui.today)
                Text(
                    "Сегодняшний день отмечается кружком на «Сегодня». Прошлые дни недели — кружками ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LastWeek(habit, ui.today, onToggle)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickTime) {
        val initial = remindAt ?: (8 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Когда напомнить") },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { remindAt = state.hour * 60 + state.minute; pickTime = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { remindAt = null; pickTime = false }) { Text("Без напоминания") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить привычку?") },
            text = { Text("Отметки останутся в журнале, но привычка пропадёт из списков.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(); onClose() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Последние 7 дней: отметить или снять отметку за прошедший день. */
@Composable
private fun LastWeek(habit: HabitInfo, today: Long, onToggle: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        // Неделя привычки: отмеченные дни — зелёные (успех), обводка плана — акцентом.
        val (doneFill, doneMark) = doneColors(colors.background.luminance() < 0.5f)
        for (d in today - 6..today) {
            val done = d in habit.doneDays
            val scheduled = Habits.isScheduled(habit, d)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dow = java.time.LocalDate.ofEpochDay(d).dayOfWeek
                Text(
                    WEEK.first { it.first == dow }.second,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == today) colors.primary else colors.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(36.dp)
                        .background(if (done) doneFill else colors.surface, CircleShape)
                        .border(1.5.dp, if (scheduled) colors.primary else colors.outlineVariant, CircleShape)
                        .clickable { onToggle(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        java.time.LocalDate.ofEpochDay(d).dayOfMonth.toString(),
                        color = if (done) doneMark else colors.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** Тепловая карта года: столбец — неделя, строка — день недели. */
@Composable
private fun Heatmap(habit: HabitInfo, today: Long) {
    val cells = remember(habit, today) { Habits.heatmap(habit, today) }
    // Отмеченные дни — зелёные, как отметки привычки.
    val done = doneColors(MaterialTheme.colorScheme.background.luminance() < 0.5f).first
    val missed = MaterialTheme.colorScheme.surfaceVariant
    val off = MaterialTheme.colorScheme.surfaceContainer
    Canvas(Modifier.fillMaxWidth().aspectRatio(cells.size / 7f)) {
        val weeks = cells.size
        val gap = 2.dp.toPx()
        val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
        cells.forEachIndexed { w, week ->
            week.forEachIndexed { i, c ->
                if (c.future) return@forEachIndexed
                val color = when {
                    c.done -> done
                    c.scheduled -> missed
                    else -> off
                }
                drawRoundRect(color, Offset(w * (cell + gap), i * (cell + gap)), Size(cell, cell), CornerRadius(cell / 4))
            }
        }
    }
}

/** Вкладка «Привычки» — вместо «Итогов», когда статистика выключена в «Функциях». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsTabScreen(
    ui: PinUi,
    onHabitOpen: (String?) -> Unit,
    onHabitToggle: (String) -> Unit,
    onFocus: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Привычки", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") } },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { onHabitOpen(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Привычка") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            if (ui.habits.isEmpty()) {
                Text(
                    "Ежедневные действия с сериями: зарядка, вода, чтение. Отмечаются кружком здесь и на «Сегодня»; " +
                        "можно поставить напоминание.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            ui.habits.forEach { h ->
                HabitRow(ui.entries[h.id]?.title ?: "", h, ui.today, onToggle = { onHabitToggle(h.id) }, onOpen = { onHabitOpen(h.id) })
            }
            if (com.sonnik.pin.engine.Feature.FOCUS.on()) {
                TextButton(onClick = onFocus, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) { Text("⏱ Начать фокус") }
            }
        }
    }
}
