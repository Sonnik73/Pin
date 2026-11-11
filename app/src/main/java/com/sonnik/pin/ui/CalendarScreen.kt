package com.sonnik.pin.ui

import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.offset
import com.sonnik.pin.engine.Priority
import java.time.ZoneId
import com.sonnik.pin.engine.PhoneEvents
import com.sonnik.pin.engine.PhoneEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.sonnik.pin.data.toMoment
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Что лежит в одном дне календаря. */
private sealed interface DayItem {
    val entry: EntryEntity

    /** Открытая задача (или текущий повтор периодической). */
    data class Open(override val entry: EntryEntity) : DayItem

    /** Будущий повтор периодической задачи — ещё не наступил, отметить нельзя. */
    data class Projected(override val entry: EntryEntity) : DayItem

    /** Выполнено в этот день. */
    data class Done(override val entry: EntryEntity, val event: JournalEntity) : DayItem

    /** Важная дата с напоминанием: сам день или очередная годовщина («1 год с этого дня»). */
    data class Moment(override val entry: EntryEntity, val note: String) : DayItem
}

private val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    ui: PinUi,
    doneBetween: (Long, Long) -> Flow<List<JournalEntity>>,
    onOpen: (String) -> Unit,
    onAdd: (Long) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSettings: () -> Unit,
    phoneEvents: suspend (Long, Long) -> List<PhoneEvent> = { _, _ -> emptyList() },
    onPhoneEvent: (PhoneEvent) -> Unit = {},
    onBodyChange: (String, String) -> Unit = { _, _ -> },
    jumpTo: Long? = null,
    onJumped: () -> Unit = {},
) {
    var selected by rememberSaveable { mutableLongStateOf(ui.today) }
    var monthStart by rememberSaveable { mutableLongStateOf(LocalDate.ofEpochDay(ui.today).withDayOfMonth(1).toEpochDay()) }
    LaunchedEffect(jumpTo) {
        val day = jumpTo ?: return@LaunchedEffect
        selected = day
        monthStart = LocalDate.ofEpochDay(day).withDayOfMonth(1).toEpochDay()
        onJumped()
    }
    // Месяц или неделя; данные берём с запасом в неделю по краям — неделя может заходить в соседний месяц.
    var weekMode by rememberSaveable { mutableStateOf(false) }
    val month = LocalDate.ofEpochDay(monthStart)
    val monthEnd = month.withDayOfMonth(month.lengthOfMonth()).toEpochDay()
    val rangeFrom = monthStart - 7
    val rangeTo = monthEnd + 7
    val doneFlow = remember(monthStart) { doneBetween(rangeFrom, rangeTo) }
    val done by doneFlow.collectAsState(initial = emptyList())
    val habitsOn = com.sonnik.pin.engine.Feature.HABITS.on()
    val items = remember(ui, done, monthStart, habitsOn) {
        itemsByDay(ui, if (habitsOn) done else done.filter { ui.entries[it.entryId]?.entryType != EntryType.HABIT }, rangeFrom, rangeTo)
    }
    // События телефона перечитываются при смене месяца и при возврате в приложение.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val zone = remember { ZoneId.systemDefault() }
    val phone by produceState(emptyMap<Long, List<PhoneEvent>>(), monthStart, resumes) {
        value = PhoneEvents.byDay(phoneEvents(rangeFrom, rangeTo), zone, rangeFrom, rangeTo)
    }

    val week = com.sonnik.pin.engine.Heat.weekOf(selected)
    fun go(step: Int) {
        if (weekMode) {
            selected += 7L * step
            monthStart = LocalDate.ofEpochDay(selected).withDayOfMonth(1).toEpochDay()
        } else {
            monthStart = month.plusMonths(step.toLong()).toEpochDay()
        }
    }
    val title = if (weekMode) {
        val a = LocalDate.ofEpochDay(week.first)
        val b = LocalDate.ofEpochDay(week.last)
        if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} ${RuText.dayMonth(b.dayOfMonth, b.monthValue).substringAfter(' ')}"
        else "${RuText.dayMonth(a.dayOfMonth, a.monthValue)} – ${RuText.dayMonth(b.dayOfMonth, b.monthValue)}"
    } else {
        "${MONTHS[month.monthValue - 1]} ${month.year}"
    }
    val sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()

    // «Календарь без прокрутки»: панель дня поднимается ровно до подсказок под сеткой (высота сетки меряется).
    val fit = com.sonnik.pin.engine.Feature.CALENDAR_FIT.on()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Где кончаются подсказки — от верха экрана вкладки (в пикселях окна); меряется после раскладки.
    var screenTop by remember { mutableStateOf(0f) }
    var bodyTop by remember { mutableStateOf(0f) }
    var gridPx by remember { mutableStateOf(0) }

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { screenTop = it.boundsInWindow().top },
    ) {
    // Высота от верха вкладки до низа подсказок: шапка (до тела) + сетка с подсказками. От прокрутки не зависит.
    val gridHeight = if (gridPx == 0) 0.dp else with(density) { ((bodyTop - screenTop).coerceAtLeast(0f) + gridPx).toDp() }
    val fitPeek = maxHeight - gridHeight - 8.dp
    // Сетка с подсказками не помещается (маленький экран, крупный шрифт) — остаётся прокрутка, как раньше.
    val fits = fit && gridHeight > 0.dp && fitPeek >= 120.dp
    // Панель дня: в режиме недели — почти весь экран, в режиме месяца — под сеткой; тянется вверх.
    val peek = when {
        fits -> fitPeek
        weekMode -> maxHeight - 230.dp
        else -> (maxHeight * 0.36f).coerceAtLeast(150.dp)
    }
    androidx.compose.material3.BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = peek,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { go(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = if (weekMode) "Предыдущая неделя" else "Предыдущий месяц")
                    }
                    IconButton(onClick = { go(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = if (weekMode) "Следующая неделя" else "Следующий месяц")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
                },
            )
        },
        sheetContent = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(RuText.headline(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.FilledTonalButton(onClick = { onAdd(maxOf(selected, ui.today)) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Задача")
                }
            }
            val dayItems = items[selected].orEmpty()
            val dayPhone = phone[selected].orEmpty()
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = peek), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (dayItems.isEmpty() && dayPhone.isEmpty()) {
                    item {
                        Text(
                            "Ничего не запланировано.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(dayPhone, key = { "phone-${it.id}-${it.begin}" }) { e ->
                    PhoneEventRow(e, PhoneEvents.describe(e, selected, zone)) { onPhoneEvent(e) }
                }
                items(dayItems, key = { "${it::class.simpleName}-${it.entry.id}-${(it as? DayItem.Done)?.event?.id}" }) { item ->
                    DayItemRow(item, ui.today, selected, onOpen = { onOpen(item.entry.id) }, onToggle = onToggle, onBody = { onBodyChange(item.entry.id, it) })
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .onGloballyPositioned { bodyTop = it.boundsInWindow().top }
                .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState())),
        ) {
          Column(Modifier.fillMaxWidth().onSizeChanged { gridPx = it.height }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    listOf(false to "Месяц", true to "Неделя").forEachIndexed { i, (w, label) ->
                        SegmentedButton(
                            selected = weekMode == w,
                            onClick = { weekMode = w },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) { Text(label, maxLines = 1) }
                    }
                }
                val todayMonth = LocalDate.ofEpochDay(ui.today).withDayOfMonth(1).toEpochDay()
                if (monthStart != todayMonth || selected != ui.today) {
                    TextButton(onClick = { monthStart = todayMonth; selected = ui.today }) { Text("Сегодня") }
                }
            }
            // Свайп по сетке листает месяцы (или недели).
            var drag by remember { mutableStateOf(0f) }
            val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
            Box(
                Modifier.pointerInput(weekMode, monthStart) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag > threshold) go(-1) else if (drag < -threshold) go(1)
                            drag = 0f
                        },
                        onHorizontalDrag = { _, dx -> drag += dx },
                    )
                },
            ) {
                MonthGrid(month, ui.today, selected, items, phone, week = if (weekMode) week else null) {
                    selected = it
                    val m = LocalDate.ofEpochDay(it).withDayOfMonth(1).toEpochDay()
                    if (m != monthStart) monthStart = m
                }
            }
            CalendarLegend(phone.isNotEmpty())
          }
            if (!fits) Spacer(Modifier.height(peek))
        }
    }
    }
}

private fun itemsByDay(ui: PinUi, done: List<JournalEntity>, from: Long, to: Long): Map<Long, List<DayItem>> {
    val result = HashMap<Long, MutableList<DayItem>>()
    fun add(day: Long, item: DayItem) {
        if (day in from..to) result.getOrPut(day) { ArrayList() } += item
    }
    for (entry in ui.entries.values) {
        if (entry.disabled) continue
        val due = entry.dueDay ?: continue
        if (entry.entryStatus == TaskStatus.OPEN) {
            add(due, DayItem.Open(entry))
            val schedule = entry.schedule
            if (schedule != null) {
                val start = maxOf(entry.originalDay ?: due, due)
                for (d in RepeatRule.occurrences(schedule.rule, schedule.anchor, maxOf(start + 1, from), to)) {
                    add(d, DayItem.Projected(entry))
                }
            }
        }
    }
    // Важные даты «каждый год/месяц/неделю» — в их дни, как дни рождения.
    for (entry in ui.entries.values) {
        val m = entry.toMoment() ?: continue
        for (d in com.sonnik.pin.engine.Moments.calendarDays(m, from, to)) add(d, DayItem.Moment(entry, com.sonnik.pin.engine.Moments.calendarNote(m, d)))
    }
    // Привычка в день — одна строка, даже если в журнале две отметки (например, с двух устройств).
    for (event in done.distinctBy { if (ui.entries[it.entryId]?.entryType == EntryType.HABIT) "${it.entryId}/${it.day}" else "e${it.id}" }) {
        val entry = ui.entries[event.entryId] ?: continue
        add(event.day, DayItem.Done(entry, event))
    }
    return result.mapValues { (_, list) ->
        list.sortedWith(
            compareBy<DayItem> { if (it is DayItem.Done) 1 else 0 }
                .thenByDescending { it.entry.priority }
                .thenBy { it.entry.timeMinutes ?: Int.MAX_VALUE },
        )
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    today: Long,
    selected: Long,
    items: Map<Long, List<DayItem>>,
    phone: Map<Long, List<PhoneEvent>>,
    week: LongRange? = null,
    onSelect: (Long) -> Unit,
) {
    val first = month.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val weeks = (offset + days + 6) / 7
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEachIndexed { i, name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i >= 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        // Неделя — одна строка выбранной недели (дни соседнего месяца тоже видны).
        val rows: List<List<Long?>> = if (week != null) {
            listOf(week.toList())
        } else {
            (0 until weeks).map { w ->
                (0 until 7).map { c ->
                    val dom = w * 7 + c - offset + 1
                    if (dom in 1..days) first.withDayOfMonth(dom).toEpochDay() else null
                }
            }
        }
        for (row in rows) {
            Row(Modifier.fillMaxWidth()) {
                for (day in row) {
                    Box(Modifier.weight(1f).aspectRatio(if (week != null) 0.8f else 1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(LocalDate.ofEpochDay(day).dayOfMonth, day == today, day == selected, items[day].orEmpty(), phone[day].orEmpty().isNotEmpty()) { onSelect(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(dom: Int, isToday: Boolean, isSelected: Boolean, items: List<DayItem>, hasPhone: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Тепловая карта: чем больше дел в день, тем заметнее подкраска клетки.
    // Будущие повторы не считаем: ежедневная зарядка иначе красит весь месяц одинаково.
    val load = items.count {
        it is DayItem.Open && it.entry.entryType != EntryType.HABIT &&
            it.entry.entryType != EntryType.BIRTHDAY && it.entry.entryType != EntryType.HOLIDAY
    }
    val heat = com.sonnik.pin.engine.Heat.alpha(com.sonnik.pin.engine.Heat.level(load))
    val background = if (isSelected) colors.primary else colors.primary.copy(alpha = heat)
    // Праздник — красное число, как в бумажном календаре.
    // Красное число — только у выходных (и своих праздников); памятные и военные даты видны в списке дня.
    val holiday = items.any {
        it.entry.entryType == EntryType.HOLIDAY && it !is DayItem.Done &&
            com.sonnik.pin.engine.Holidays.kindOf(it.entry.id) == com.sonnik.pin.engine.HolidayKind.DAY_OFF
    }
    val textColor = when {
        isSelected -> colors.onPrimary
        holiday -> HolidayRed
        else -> colors.onSurface
    }
    Column(
        Modifier
            .padding(3.dp)
            .fillMaxSize()
            .background(background, CircleShape)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dom.toString(),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || holiday) FontWeight.Bold else null,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(12.dp),
        ) {
            // День рождения — тортик, он заметен на любом фоне; остальное — точки (не больше трёх).
            if (items.any { it.entry.entryType == EntryType.BIRTHDAY }) {
                Text("🎂", fontSize = 10.sp, lineHeight = 11.sp)
            } else if (items.any { it is DayItem.Moment }) {
                Text("📍", fontSize = 9.sp, lineHeight = 11.sp)
            }
            // Важная невыполненная задача — красный «!» перед точками.
            val tasks = items.filter { it.entry.entryType != EntryType.HABIT && it !is DayItem.Moment }
            // День рождения — не «важная задача»: у него свой значок 🎂.
            val important = tasks.any {
                it !is DayItem.Done && it.entry.entryPriority == Priority.HIGH &&
                    it.entry.entryType != EntryType.HOLIDAY && it.entry.entryType != EntryType.BIRTHDAY
            }
            if (important) {
                Text(
                    "!",
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) colors.onPrimary else ImportantColor,
                )
            }
            val dots = tasks.filter { it.entry.entryType != EntryType.BIRTHDAY && it.entry.entryType != EntryType.HOLIDAY }.take(if (important) 2 else 3).map {
                when (it) {
                    is DayItem.Open -> if (isSelected) colors.onPrimary else colors.primary
                    is DayItem.Projected -> if (isSelected) colors.onPrimary.copy(alpha = 0.6f) else colors.tertiary
                    is DayItem.Done -> if (isSelected) colors.onPrimary.copy(alpha = 0.6f) else colors.outline
                    is DayItem.Moment -> colors.tertiary
                }
            }
            dots.forEach { Box(Modifier.size(6.dp).background(it, CircleShape)) }
            // Событие календаря телефона — квадратик, чтобы не путать с задачами.
            if (hasPhone) {
                Box(Modifier.size(6.dp).background(if (isSelected) colors.onPrimary else PhoneColor, RoundedCornerShape(1.dp)))
            }
        }
    }
}

/** Цвет праздничного числа: красный в светлой теме, мягче — в тёмной. */
private val HolidayRed: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF8A80) else Color(0xFFD32F2F)

/** Метка важной задачи: тот же красный, что у праздничного числа, но заметнее точек. */
private val ImportantColor: Color
    @Composable get() = HolidayRed

/** Цвет событий календаря телефона — бирюзовый, отдельно от задач, повторов и праздников. */
private val PhoneColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF4DD0C4) else Color(0xFF00897B)

@Composable
private fun PhoneEventRow(e: PhoneEvent, time: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(e.color?.let { Color(it) } ?: PhoneColor, RoundedCornerShape(3.dp)))
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(e.title, color = MaterialTheme.colorScheme.onSurface)
            Text(
                listOf(time, "📅 " + e.calendar.ifBlank { "календарь" }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Подсказка под календарём: что значат значки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(phone: Boolean) {
    val colors = MaterialTheme.colorScheme
    @Composable
    fun dot(color: Color, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(" $label", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        dot(colors.primary, "задача")
        dot(colors.tertiary, "повтор")
        dot(colors.outline, "сделано")
        if (phone) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(PhoneColor, RoundedCornerShape(1.dp)))
                Text(" календарь телефона", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Row {
            Text("!", style = MaterialTheme.typography.labelSmall, color = ImportantColor, fontWeight = FontWeight.Black)
            Text(" важная", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Text("🎂 день рождения", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Row {
            Text("12", style = MaterialTheme.typography.labelSmall, color = HolidayRed, fontWeight = FontWeight.Bold)
            Text(" праздник", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayItemRow(item: DayItem, today: Long, day: Long, onOpen: () -> Unit, onToggle: (String, Boolean) -> Unit, onBody: (String) -> Unit) {
    val entry = item.entry
    var expanded by rememberSaveable(entry.id, item::class.simpleName) { mutableStateOf(false) }
    // Подпункты раскрываются только у задачи, которую ещё можно отметить; будущие повторы и выполненное — только смотреть.
    val canExpand = item is DayItem.Open && entry.entryType != EntryType.HOLIDAY && entry.entryType != EntryType.BIRTHDAY
    PinCard(Modifier.padding(horizontal = 12.dp, vertical = 3.dp), color = if (item is DayItem.Open) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
    Column {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 4.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item) {
            is DayItem.Open -> if (entry.entryType == EntryType.HOLIDAY) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text(com.sonnik.pin.engine.Holidays.kindOf(entry.id).emoji) }
            } else {
                RoundCheck(false, { onToggle(entry.id, true) }, if (entry.entryPriority == Priority.HIGH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            is DayItem.Done -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (entry.entryType == EntryType.HABIT) Text("🔥") else Checkbox(checked = true, onCheckedChange = null, enabled = false)
            }
            is DayItem.Projected -> Spacer(Modifier.size(48.dp))
            is DayItem.Moment -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text("📍") }
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            val habit = entry.entryType == EntryType.HABIT
            Text(
                (if (item !is DayItem.Done && entry.entryPriority == Priority.HIGH && entry.entryType != EntryType.BIRTHDAY) "❗ " else "") + entry.title,
                textDecoration = if (item is DayItem.Done && !habit) TextDecoration.LineThrough else null,
                color = if (item is DayItem.Open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val meta = buildList {
                if (habit) add("привычка выполнена")
                if (item !is DayItem.Done && entry.entryPriority == Priority.HIGH && entry.entryType != EntryType.BIRTHDAY) add("важно")
                // У привычки время — это напоминание, а не срок.
                if (!habit) entry.timeMinutes?.let { add(RuText.time(it)) }
                if (entry.entryType == EntryType.RECURRING) entry.rule?.let { add("↻ " + RepeatRule.describe(it)) }
                if (entry.entryType == EntryType.HOLIDAY) {
                    add(com.sonnik.pin.engine.Holidays.byEntryId(entry.id)?.kind?.title ?: "праздник")
                }
                if (entry.entryType == EntryType.BIRTHDAY) {
                    add("🎂 день рождения" + (Occasions.ageOn(entry.birthYear, day)?.let { ", $it ${RuText.plural(it, "год", "года", "лет")}" } ?: ""))
                }
                if (item is DayItem.Projected) add("будущий повтор")
                if (item is DayItem.Moment) add(item.note)
                if (item is DayItem.Open && entry.rollovers > 0) add(RuText.rollovers(entry.rollovers))
                if (item is DayItem.Done && !item.event.onTime) add("с опозданием")
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canExpand) BodyToggle(entry.body, expanded, { expanded = it }, Modifier.padding(top = 2.dp))
            AttachmentButton(com.sonnik.pin.engine.Attachments.parse(entry.attachments), LocalOpenAttachment.current, Modifier.padding(top = 2.dp))
        }
    }
    if (canExpand && expanded && entry.body.isNotBlank()) {
        ExpandedBody(entry.body, onBody, Modifier.fillMaxWidth().padding(start = 52.dp, end = 14.dp, bottom = 10.dp))
    }
    }
    }
}
