package com.sonnik.pin.ui

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonnik.pin.engine.EntryType

/** Значение editorId для новой задачи. */
private const val NEW = ""

internal enum class Tab(val title: String, val icon: ImageVector, val feature: com.sonnik.pin.engine.Feature? = null) {
    TODAY("Сегодня", Icons.Filled.Home),
    CALENDAR("Календарь", Icons.Filled.DateRange),
    PEOPLE("Люди", Icons.Filled.Person, com.sonnik.pin.engine.Feature.PEOPLE),
    PLANS("Планы", Icons.Filled.Star, com.sonnik.pin.engine.Feature.PLANS),
    STATS("Итоги", PinIcons.Chart, com.sonnik.pin.engine.Feature.STATS),
    /** Встаёт на место «Итогов», когда их выключили, а привычки — нет. */
    HABITS("Привычки", Icons.Filled.CheckCircle, com.sonnik.pin.engine.Feature.HABITS),
    ;

    fun visible(disabled: Set<com.sonnik.pin.engine.Feature>): Boolean = when (this) {
        HABITS -> com.sonnik.pin.engine.Feature.STATS in disabled && com.sonnik.pin.engine.Feature.HABITS !in disabled
        else -> feature == null || feature !in disabled
    }
}

private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_HOLIDAYS = "holidays"
private const val SCREEN_CHANGELOG = "changelog"
private const val SCREEN_LISTS = "lists"
private const val SCREEN_MATRIX = "matrix"
private const val SCREEN_ARCHIVE = "archive"
private const val SCREEN_FEATURES = "features"
private const val SCREEN_SEARCH = "search"
const val SCREEN_REVIEW = "review"
private const val SCREEN_HABIT = "habit:"
private const val SCREEN_FOCUS = "focus"
private const val SCREEN_PERSON = "person:"

@Composable
fun AppRoot(viewModel: PinViewModel) {
    val features = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.sonnik.pin.PinApp).featureSettings
    val disabled by features.disabled.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(LocalDisabledFeatures provides disabled) {
    PinTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            // null — открыт список; "" — новая задача; иначе id редактируемой.
            var editorId by rememberSaveable { mutableStateOf<String?>(null) }
            var newDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
            var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
            // null — вкладки; "settings", "holidays", "person:<id>" ("person:" — новый человек).
            var screen by rememberSaveable { mutableStateOf<String?>(null) }
            val state = ui
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }

            // Экран, о котором попросили извне (кнопка «Разобрать» в утренней сводке).
            val pending by viewModel.pendingScreen.collectAsStateWithLifecycle()
            LaunchedEffect(pending) {
                pending?.let { screen = it; editorId = null; viewModel.pendingScreen.value = null }
            }
            val message by viewModel.message.collectAsStateWithLifecycle()
            // «Выполнено · Отменить»: случайную отметку можно снять сразу, не ища задачу.
            val snackbar = remember { SnackbarHostState() }
            val undo by viewModel.undoDone.collectAsStateWithLifecycle()
            LaunchedEffect(undo) {
                val (id, title) = undo ?: return@LaunchedEffect
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar("Выполнено: $title", actionLabel = "Отменить", withDismissAction = true, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.setDone(id, false)
                viewModel.undoDone.value = null
            }
            val focusState by viewModel.focus.collectAsStateWithLifecycle()
            message?.let { text ->
                AlertDialog(
                    onDismissRequest = { viewModel.message.value = null },
                    text = { Text(text) },
                    confirmButton = { TextButton(onClick = { viewModel.message.value = null }) { Text("OK") } },
                )
            }

            WhatsNewOnUpdate(openChangelog = { editorId = null; screen = SCREEN_CHANGELOG })

            NotificationPermission(asked = state.settings.notificationsAsked) {
                viewModel.updateSettings { it.copy(notificationsAsked = true) }
            }

            val id = editorId
            val current = screen
            // Записи без обычного редактора открываются в своих экранах.
            fun open(entryId: String) {
                val e = state.entries[entryId]
                when (e?.entryType) {
                    EntryType.BIRTHDAY, EntryType.GIFT -> screen = SCREEN_PERSON + (e?.personId ?: "")
                    EntryType.HOLIDAY -> screen = SCREEN_HOLIDAYS
                    EntryType.PLAN -> { tab = Tab.PLANS; screen = null }
                    EntryType.HABIT -> screen = SCREEN_HABIT + entryId
                    else -> editorId = entryId
                }
            }
            when {
                id != null -> {
                    val entry = if (id == NEW) null else state.entries[id]
                    key(id) {
                        EditorScreen(
                            entry = entry,
                            today = state.today,
                            newDay = if (newDay == Long.MIN_VALUE) state.today else newDay,
                            allTags = state.allTags,
                            lists = state.lists,
                            onSave = { viewModel.save(entry?.id, it) },
                            onDelete = { entry?.let { viewModel.delete(it.id) } },
                            onToggleDone = { done -> entry?.let { viewModel.setDone(it.id, done) } },
                            onClose = { editorId = null },
                            onFocus = entry?.let { e -> { viewModel.focusTask.value = e.id; editorId = null; screen = SCREEN_FOCUS } },
                        )
                    }
                }
                current == SCREEN_SETTINGS -> SettingsScreen(
                    settings = state.settings,
                    onDayBoundary = viewModel::setDayBoundary,
                    onChange = viewModel::updateSettings,
                    onHolidays = { screen = SCREEN_HOLIDAYS },
                    onExport = viewModel::exportTo,
                    onImport = viewModel::importFrom,
                    onDriveConnected = viewModel::driveConnected,
                    onDriveDisconnect = viewModel::driveDisconnect,
                    onSyncNow = viewModel::syncNow,
                    onMessage = { viewModel.message.value = it },
                    onChangelog = { screen = SCREEN_CHANGELOG },
                    onClose = { screen = null },
                    onLists = { screen = SCREEN_LISTS },
                    onFeatures = { screen = SCREEN_FEATURES },
                    listsCount = state.lists.size,
                )
                current == SCREEN_FEATURES -> FeaturesScreen(
                    disabled,
                    onSet = { f, on ->
                        features.set(f, on)
                        if (f == com.sonnik.pin.engine.Feature.HABITS) viewModel.rescheduleNotifications()
                    },
                    onClose = { screen = SCREEN_SETTINGS },
                )
                current == SCREEN_LISTS -> ListsScreen(
                    lists = state.lists,
                    counts = state.entries.values.filter { it.listId != null && it.completedDay == null }.groupingBy { it.listId!! }.eachCount(),
                    onSave = viewModel::saveList,
                    onDelete = viewModel::deleteList,
                    onClose = { screen = SCREEN_SETTINGS },
                )
                current != null && current.startsWith(SCREEN_HABIT) -> {
                    val habitId = current.removePrefix(SCREEN_HABIT).ifEmpty { null }
                    key(current) {
                        HabitScreen(
                            ui = state,
                            habitId = habitId,
                            onSave = { title, rule, remindAt -> viewModel.saveHabit(habitId, title, rule, remindAt) },
                            onToggle = { day -> habitId?.let { viewModel.toggleHabit(it, day) } },
                            onDelete = { habitId?.let(viewModel::delete) },
                            onClose = { screen = null },
                        )
                    }
                }
                current == SCREEN_FOCUS -> {
                    val events by viewModel.statEvents.collectAsStateWithLifecycle()
                    val chosen by viewModel.focusTask.collectAsStateWithLifecycle()
                    val taskId = focusState?.taskId ?: chosen
                    FocusScreen(
                        state = focusState,
                        taskTitle = taskId?.let { state.entries[it]?.title },
                        todayMinutes = focusMinutes(events, state.today),
                        onStart = { viewModel.startFocus(taskId) },
                        onStop = viewModel::stopFocus,
                        onClose = { screen = null },
                    )
                }
                current == SCREEN_REVIEW -> ReviewScreen(state, onAction = viewModel::review, onClose = { screen = null })
                current == SCREEN_MATRIX -> MatrixScreen(
                    state,
                    onOpen = ::open,
                    onToggle = viewModel::setDone,
                    onImportant = viewModel::setImportant,
                    onClose = { screen = null },
                )
                current == SCREEN_ARCHIVE -> AllEntriesScreen(state, onOpen = ::open, onClose = { screen = null }, initialState = com.sonnik.pin.engine.SearchState.DONE)
                current == SCREEN_SEARCH -> AllEntriesScreen(state, onOpen = ::open, onClose = { screen = null })
                current == SCREEN_CHANGELOG -> ChangelogScreen(onClose = { screen = SCREEN_SETTINGS })
                current == SCREEN_HOLIDAYS -> HolidaysScreen(
                    ui = state,
                    onSave = viewModel::saveHoliday,
                    onEnabled = { hid, on -> viewModel.setDisabled(hid, !on) },
                    onDelete = viewModel::delete,
                    onClose = { screen = null },
                )
                current != null && current.startsWith(SCREEN_PERSON) -> {
                    val personId = current.removePrefix(SCREEN_PERSON).ifEmpty { null }
                    key(current) {
                        PersonScreen(
                            ui = state,
                            personId = personId,
                            onSave = { name, relation, note, birthday, remind ->
                                viewModel.savePerson(personId, name, relation, note, birthday, remind)
                            },
                            onDelete = { personId?.let(viewModel::deletePerson) },
                            onSaveGift = viewModel::saveGift,
                            onGiftStatus = viewModel::setGiftStatus,
                            onDeleteEntry = viewModel::delete,
                            onClose = { screen = null },
                        )
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    // Вкладку выключили в «Функциях» — показываем «Сегодня».
                    val activeTab = when {
                        tab.visible(disabled) -> tab
                        // «Итоги» выключили — на их место встают «Привычки», и наоборот.
                        tab == Tab.STATS && Tab.HABITS.visible(disabled) -> Tab.HABITS
                        tab == Tab.HABITS && Tab.STATS.visible(disabled) -> Tab.STATS
                        else -> Tab.TODAY
                    }
                    Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
                        when (activeTab) {
                            Tab.TODAY -> TodayScreen(
                                ui = state,
                                onOpen = ::open,
                                onAdd = { newDay = state.today; editorId = NEW },
                                onToggle = viewModel::setDone,
                                onSettings = { screen = SCREEN_SETTINGS },
                                onQuickAdd = viewModel::quickAdd,
                                onSearch = { screen = SCREEN_SEARCH },
                                onReview = { screen = SCREEN_REVIEW },
                                onHabitToggle = { viewModel.toggleHabit(it, state.today) },
                                onHabitOpen = { screen = SCREEN_HABIT + it },
                                focus = focusState,
                                onFocus = { screen = SCREEN_FOCUS },
                                onMatrix = { screen = SCREEN_MATRIX },
                                onArchive = { screen = SCREEN_ARCHIVE },
                                onBodyChange = viewModel::setBody,
                            )
                            Tab.CALENDAR -> CalendarScreen(
                                ui = state,
                                doneBetween = viewModel::doneBetween,
                                onOpen = ::open,
                                onAdd = { newDay = it; editorId = NEW },
                                onToggle = viewModel::setDone,
                                onSettings = { screen = SCREEN_SETTINGS },
                                phoneEvents = { from, to -> com.sonnik.pin.data.PhoneCalendar.between(appContext, from, to) },
                                onPhoneEvent = { com.sonnik.pin.data.PhoneCalendar.open(appContext, it) },
                                onBodyChange = viewModel::setBody,
                            )
                            Tab.PEOPLE -> PeopleScreen(
                                ui = state,
                                onOpen = { screen = SCREEN_PERSON + it },
                                onAdd = { screen = SCREEN_PERSON },
                                onSettings = { screen = SCREEN_SETTINGS },
                            )
                            Tab.STATS -> {
                                val events by viewModel.statEvents.collectAsStateWithLifecycle()
                                StatsScreen(
                                    state,
                                    events,
                                    onOpen = ::open,
                                    onSettings = { screen = SCREEN_SETTINGS },
                                    onHabitOpen = { screen = SCREEN_HABIT + (it ?: "") },
                                    onHabitToggle = { viewModel.toggleHabit(it, state.today) },
                                    onFocus = { viewModel.focusTask.value = null; screen = SCREEN_FOCUS },
                                )
                            }
                            Tab.HABITS -> HabitsTabScreen(
                                state,
                                onHabitOpen = { screen = SCREEN_HABIT + (it ?: "") },
                                onHabitToggle = { viewModel.toggleHabit(it, state.today) },
                                onFocus = { viewModel.focusTask.value = null; screen = SCREEN_FOCUS },
                                onSettings = { screen = SCREEN_SETTINGS },
                            )
                            Tab.PLANS -> PlansScreen(
                                ui = state,
                                onSave = viewModel::savePlan,
                                onToTask = viewModel::planToTask,
                                onBodyChange = viewModel::setBody,
                                onDelete = viewModel::delete,
                                onSettings = { screen = SCREEN_SETTINGS },
                            )
                        }
                    }
                    SnackbarHost(snackbar, Modifier.fillMaxWidth())
                    PinNavigationBar(activeTab) { tab = it }
                }
            }
        }
    }
    }
}

/** Android 13+: один раз спросить разрешение на уведомления (сводка и напоминания). */
@Composable
private fun NotificationPermission(asked: Boolean, onAsked: () -> Unit) {
    if (Build.VERSION.SDK_INT < 33 || asked) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAsked() }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/**
 * Нижняя панель. Подписи — в одну строку: при крупном системном шрифте «Календарь» иначе переносится
 * на две строки и сдвигает значок.
 */
@Composable
internal fun PinNavigationBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    // Подпись не растёт с системным шрифтом: пять вкладок на узком экране иначе не помещаются.
    val labelSize = 12.sp / fontScale
    NavigationBar {
        val disabled = LocalDisabledFeatures.current
        Tab.entries.filter { it.visible(disabled) }.forEach { t ->
            NavigationBarItem(
                selected = tab == t,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.title, fontSize = labelSize, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip) },
            )
        }
    }
}
