package com.sonnik.pin.ui

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

private enum class Tab(val title: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Home),
    CALENDAR("Календарь", Icons.Filled.DateRange),
    PEOPLE("Люди", Icons.Filled.Person),
    PLANS("Планы", Icons.Filled.Star),
    STATS("Итоги", PinIcons.Chart),
}

private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_HOLIDAYS = "holidays"
private const val SCREEN_CHANGELOG = "changelog"
private const val SCREEN_SEARCH = "search"
const val SCREEN_REVIEW = "review"
private const val SCREEN_PERSON = "person:"

@Composable
fun AppRoot(viewModel: PinViewModel) {
    PinTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
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
                            onSave = { viewModel.save(entry?.id, it) },
                            onDelete = { entry?.let { viewModel.delete(it.id) } },
                            onToggleDone = { done -> entry?.let { viewModel.setDone(it.id, done) } },
                            onClose = { editorId = null },
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
                )
                current == SCREEN_REVIEW -> ReviewScreen(state, onAction = viewModel::review, onClose = { screen = null })
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
                    Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
                        when (tab) {
                            Tab.TODAY -> TodayScreen(
                                ui = state,
                                onOpen = ::open,
                                onAdd = { newDay = state.today; editorId = NEW },
                                onToggle = viewModel::setDone,
                                onSettings = { screen = SCREEN_SETTINGS },
                                onQuickAdd = { viewModel.save(null, it.toTaskDraft()) },
                                onSearch = { screen = SCREEN_SEARCH },
                                onReview = { screen = SCREEN_REVIEW },
                            )
                            Tab.CALENDAR -> CalendarScreen(
                                ui = state,
                                doneBetween = viewModel::doneBetween,
                                onOpen = ::open,
                                onAdd = { newDay = it; editorId = NEW },
                                onToggle = viewModel::setDone,
                                onSettings = { screen = SCREEN_SETTINGS },
                            )
                            Tab.PEOPLE -> PeopleScreen(
                                ui = state,
                                onOpen = { screen = SCREEN_PERSON + it },
                                onAdd = { screen = SCREEN_PERSON },
                                onSettings = { screen = SCREEN_SETTINGS },
                            )
                            Tab.STATS -> {
                                val events by viewModel.statEvents.collectAsStateWithLifecycle()
                                StatsScreen(state, events, onOpen = ::open, onSettings = { screen = SCREEN_SETTINGS })
                            }
                            Tab.PLANS -> PlansScreen(
                                ui = state,
                                onSave = viewModel::savePlan,
                                onToTask = viewModel::planToTask,
                                onDelete = viewModel::delete,
                                onSettings = { screen = SCREEN_SETTINGS },
                            )
                        }
                    }
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.title) },
                            )
                        }
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
