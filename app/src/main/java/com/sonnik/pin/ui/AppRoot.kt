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
import androidx.compose.material3.CircularProgressIndicator
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

/** Значение editorId для новой задачи. */
private const val NEW = ""

private enum class Tab(val title: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Home),
    CALENDAR("Календарь", Icons.Filled.DateRange),
}

@Composable
fun AppRoot(viewModel: PinViewModel) {
    PinTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            // null — открыт список; "" — новая задача; иначе id редактируемой.
            var editorId by rememberSaveable { mutableStateOf<String?>(null) }
            var newDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
            var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            val state = ui
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }

            NotificationPermission(asked = state.settings.notificationsAsked) {
                viewModel.updateSettings { it.copy(notificationsAsked = true) }
            }

            val id = editorId
            when {
                id != null -> {
                    val entry = if (id == NEW) null else state.entries[id]
                    key(id) {
                        EditorScreen(
                            entry = entry,
                            today = state.today,
                            newDay = if (newDay == Long.MIN_VALUE) state.today else newDay,
                            onSave = { viewModel.save(entry?.id, it) },
                            onDelete = { entry?.let { viewModel.delete(it.id) } },
                            onToggleDone = { done -> entry?.let { viewModel.setDone(it.id, done) } },
                            onClose = { editorId = null },
                        )
                    }
                }
                settingsOpen -> SettingsScreen(
                    settings = state.settings,
                    onDayBoundary = viewModel::setDayBoundary,
                    onChange = viewModel::updateSettings,
                    onClose = { settingsOpen = false },
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
                        when (tab) {
                            Tab.TODAY -> TodayScreen(
                                ui = state,
                                onOpen = { editorId = it },
                                onAdd = { newDay = state.today; editorId = NEW },
                                onToggle = viewModel::setDone,
                                onSettings = { settingsOpen = true },
                            )
                            Tab.CALENDAR -> CalendarScreen(
                                ui = state,
                                doneBetween = viewModel::doneBetween,
                                onOpen = { editorId = it },
                                onAdd = { newDay = it; editorId = NEW },
                                onToggle = viewModel::setDone,
                                onSettings = { settingsOpen = true },
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
