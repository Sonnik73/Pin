package com.sonnik.pin.ui

import android.app.Activity
import android.app.AlarmManager
import android.content.ContextWrapper
import androidx.activity.result.IntentSenderRequest
import com.sonnik.pin.sync.DriveAuth
import com.sonnik.pin.sync.FileSyncWorker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sonnik.pin.BuildConfigInfo
import com.sonnik.pin.data.SettingsEntity
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.notify.Notifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    onDayBoundary: (Int) -> Unit,
    onChange: ((SettingsEntity) -> SettingsEntity) -> Unit,
    onHolidays: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onDriveConnected: () -> Unit,
    onDriveDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onMessage: (String) -> Unit,
    onClose: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { DriveAuth.resultFromIntent(appContext, result.data) }
            .onSuccess { if (it.accessToken != null) onDriveConnected() else onMessage("Google не выдал доступ к Диску") }
            .onFailure { onMessage("Вход не удался: " + DriveAuth.describe(it)) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    val context = LocalContext.current
    var boundaryDialog by rememberSaveable { mutableStateOf(false) }
    var morningDialog by rememberSaveable { mutableStateOf(false) }
    // Разрешения могут поменять в системных настройках — перечитываем при возврате.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val canNotify = remember(resumes) { Notifier.canNotify(context) }
    val exactAlarms = remember(resumes) { canExactAlarms(context) }

    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Оформление")
            AppearanceSection()

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("День")
            SettingRow(
                title = "Граница дня",
                subtitle = "${RuText.time(settings.dayBoundaryMinutes)} — невыполненное переезжает на завтра в это время",
                onClick = { boundaryDialog = true },
            )

            SettingRow(
                title = "Праздники",
                subtitle = "Российские праздники и свои даты — годовщины и т. п.",
                onClick = onHolidays,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Уведомления")
            SwitchRow(
                title = "Утренняя сводка",
                subtitle = if (settings.morningEnabled) "в ${RuText.time(settings.morningMinutes)} — что на сегодня" else "выключена",
                checked = settings.morningEnabled,
                onClick = { if (settings.morningEnabled) morningDialog = true },
                onCheckedChange = { on -> onChange { it.copy(morningEnabled = on) } },
            )
            SwitchRow(
                title = "Напоминания по времени",
                subtitle = "для задач, у которых указано время",
                checked = settings.remindersEnabled,
                onCheckedChange = { on ->
                    // Включили — прошедшие напоминания не догоняем.
                    onChange { it.copy(remindersEnabled = on, lastReminderAt = System.currentTimeMillis()) }
                },
            )
            if (!canNotify) {
                SettingRow(
                    title = "Уведомления запрещены",
                    subtitle = "Разрешить в настройках Android",
                    warning = true,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }
            if (!exactAlarms && Build.VERSION.SDK_INT >= 31) {
                SettingRow(
                    title = "Напоминания могут опаздывать",
                    subtitle = "Разрешить точные будильники в настройках Android",
                    warning = true,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Google Диск — файлом")
            FileSyncSection(onMessage)
            Text(
                "Папкой .md-файлов (для Obsidian) — нужна разовая настройка Google Cloud, см. docs/DRIVE.md:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (settings.syncEnabled) {
                val status = when {
                    settings.lastSyncError != null -> "Ошибка: ${settings.lastSyncError}"
                    settings.lastSyncAt == 0L -> "Подключён, первая синхронизация скоро"
                    else -> "Синхронизировано: " + java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", java.util.Locale("ru"))
                        .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(settings.lastSyncAt))
                }
                SettingRow(title = "Синхронизировать сейчас", subtitle = status, warning = settings.lastSyncError != null, onClick = onSyncNow)
                if (settings.lastSyncError != null) {
                    SettingRow(title = "Войти заново", subtitle = "Если Google просит подтвердить доступ", onClick = { connectDrive(context, signIn, onDriveConnected, onMessage) })
                }
                SettingRow(title = "Отключить", subtitle = "Файлы на Диске останутся, синхронизация остановится", onClick = onDriveDisconnect)
            } else {
                SettingRow(
                    title = "Подключить Google Диск",
                    subtitle = "Папка «Pin» с .md-файлами; Pin видит только свои файлы",
                    onClick = { connectDrive(context, signIn, onDriveConnected, onMessage) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Данные")
            SettingRow(
                title = "Экспорт в zip",
                subtitle = "Все записи — папка .md-файлов, которую открывает Obsidian",
                onClick = { exportLauncher.launch("Pin-${java.time.LocalDate.now()}.zip") },
            )
            SettingRow(
                title = "Импорт из zip",
                subtitle = "Слияние с текущими данными: новее — побеждает",
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("О приложении")
            SettingRow(
                title = "Pin ${BuildConfigInfo.versionName(context)}",
                subtitle = "Таск-трекер экосистемы Крепости. Работает без интернета.",
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (boundaryDialog) {
        DayBoundaryDialog(
            current = settings.dayBoundaryMinutes,
            onPick = { onDayBoundary(it); boundaryDialog = false },
            onDismiss = { boundaryDialog = false },
        )
    }
    if (morningDialog) {
        val state = rememberTimePickerState(settings.morningMinutes / 60, settings.morningMinutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { morningDialog = false },
            title = { Text("Время сводки") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = state.hour * 60 + state.minute
                    onChange { it.copy(morningMinutes = minutes) }
                    morningDialog = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { morningDialog = false }) { Text("Отмена") } },
        )
    }
}

/** Хранение одним zip-файлом через системное окно «Сохранить в…» (Google Диск, его приложение). */
@Composable
private fun FileSyncSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? com.sonnik.pin.PinApp ?: return
    val state by app.fileSync.state.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            FileSyncWorker.attach(context, uri)
            onMessage("Файл создан. Pin будет сам сохранять в него изменения; приложение Google Диска отправит его в облако.")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            FileSyncWorker.attach(context, uri)
            onMessage("Файл подключён — данные из него сливаются с этим телефоном.")
        }
    }
    if (!state.enabled) {
        SettingRow(
            title = "Создать файл на Диске",
            subtitle = "Выберите Google Диск в окне сохранения, например «Мой диск / Pin». Без входа и Google Cloud.",
            onClick = { create.launch("Pin.zip") },
        )
        SettingRow(
            title = "Подключить существующий файл",
            subtitle = "Для второго телефона: выберите Pin.zip, созданный на первом",
            onClick = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        )
    } else {
        val name = remember(state.uri) {
            runCatching {
                context.contentResolver.query(Uri.parse(state.uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "Pin.zip"
        }
        val status = when {
            state.error != null -> "Ошибка: ${state.error}"
            state.lastAt == 0L -> "Подключён: $name"
            else -> "$name · сохранено " + java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", java.util.Locale("ru"))
                .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(state.lastAt))
        }
        SettingRow(
            title = "Сохранить сейчас",
            subtitle = status,
            warning = state.error != null,
            onClick = { FileSyncWorker.requestSoon(context, delaySeconds = 0); onMessage("Синхронизация с файлом запущена.") },
        )
        if (state.error != null) {
            SettingRow(title = "Выбрать файл заново", subtitle = "Если доступ к файлу пропал", onClick = {
                open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            })
        }
        SettingRow(title = "Отключить файл", subtitle = "Сам файл на Диске останется", onClick = { FileSyncWorker.detach(context) })
    }
}

private fun connectDrive(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onConnected: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val activity = context.findActivity() ?: return
    if (!DriveAuth.playServicesAvailable(context)) {
        onMessage("На телефоне нет сервисов Google Play — вход в Google Диск невозможен. Экспорт в zip работает без Google.")
        return
    }
    DriveAuth.authorize(activity)
        .addOnSuccessListener { result ->
            val pending = result.pendingIntent
            if (result.hasResolution() && pending != null) {
                launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            } else {
                onConnected()
            }
        }
        .addOnFailureListener { onMessage("Вход не удался: " + DriveAuth.describe(it)) }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun canExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, warning: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (onClick != null) onClick() else onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DayBoundaryDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = (0..6).map { it * 60 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Граница дня") },
        text = {
            Column {
                Text(
                    "Во сколько начинается новый день. Невыполненное переезжает на завтра в это время, " +
                        "а отмеченное ночью до границы засчитывается вчерашнему дню.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = minutes == current, onClick = { onPick(minutes) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = minutes == current, onClick = { onPick(minutes) })
                        Text(RuText.time(minutes) + if (minutes == 4 * 60) " — как в Крепости" else "")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
