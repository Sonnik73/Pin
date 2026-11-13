package com.sonnik.pin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonnik.pin.engine.ReleaseInfo
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.Updates

/** Состояние ручной проверки обновлений. */
sealed interface UpdateCheckState {
    data object Checking : UpdateCheckState
    data class UpToDate(val current: String, val latest: ReleaseInfo) : UpdateCheckState
    data class Failed(val reason: String) : UpdateCheckState
}

/** Скачивание обновления в фоне: приложение качает APK и ставит его штатным установщиком. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val bytes: Long, val total: Long?) : UpdateDownloadState
    data class Ready(val bytes: Long) : UpdateDownloadState
    data class Failed(val reason: String) : UpdateDownloadState
}

/** Круглый значок над заголовком окна. */
@Composable
private fun Badge(background: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(64.dp).background(background, CircleShape), contentAlignment = Alignment.Center) { content() }
}

/** Карточка «что установлено / что на GitHub». */
@Composable
private fun VersionCard(rows: List<Pair<String, String>>) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun ReleaseInfo.label() = version.removePrefix("v").removePrefix("V")

/** Окно ручной проверки: «Проверяем…», «Обновлений нет», «Не удалось проверить». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckDialog(state: UpdateCheckState, today: Long, onRetry: () -> Unit, onChangelog: () -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) { UpdateCheckContent(state, today, onRetry, onChangelog, onDismiss) }
}

@Composable
internal fun UpdateCheckContent(state: UpdateCheckState, today: Long, onRetry: () -> Unit, onChangelog: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    UpdatePanel(
        icon = {
            when (state) {
                UpdateCheckState.Checking -> Badge(colors.secondaryContainer) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = colors.onSecondaryContainer)
                }
                is UpdateCheckState.UpToDate -> Badge(colors.primaryContainer) { Text("✓", fontSize = 30.sp, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold) }
                is UpdateCheckState.Failed -> Badge(colors.errorContainer) { Text("!", fontSize = 30.sp, color = colors.onErrorContainer, fontWeight = FontWeight.Bold) }
            }
        },
        title = {
            Text(
                when (state) {
                    UpdateCheckState.Checking -> "Проверяем обновления…"
                    is UpdateCheckState.UpToDate -> "Обновлений нет"
                    is UpdateCheckState.Failed -> "Не удалось проверить"
                },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    UpdateCheckState.Checking -> Text(
                        "Спрашиваем GitHub о последнем выпуске",
                        textAlign = TextAlign.Center,
                        color = colors.onSurfaceVariant,
                    )
                    is UpdateCheckState.UpToDate -> {
                        Text("У вас самая свежая версия Pin.", textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                        VersionCard(
                            listOfNotNull(
                                "Установлена" to state.current,
                                "На GitHub" to state.latest.label(),
                                state.latest.publishedDay?.let { "Вышла" to RuText.date(it, today) },
                            ),
                        )
                    }
                    is UpdateCheckState.Failed -> Text(
                        state.reason.replaceFirstChar { it.uppercase() } + ". Проверьте интернет и попробуйте ещё раз.",
                        textAlign = TextAlign.Center,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                UpdateCheckState.Checking -> TextButton(onClick = onDismiss) { Text("Скрыть") }
                is UpdateCheckState.UpToDate -> Button(onClick = onDismiss) { Text("Готово") }
                is UpdateCheckState.Failed -> Button(onClick = onRetry) { Text("Повторить") }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateCheckState.UpToDate -> TextButton(onClick = onChangelog) { Text("Что нового") }
                is UpdateCheckState.Failed -> TextButton(onClick = onDismiss) { Text("Закрыть") }
                else -> {}
            }
        },
    )
}

/** «Новая версия Pin»: было → стало, дата и размер, что нового, скачивание и кнопка «Установить». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    current: String,
    download: UpdateDownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onLater: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onLater) { UpdateOfferContent(release, current, download, onDownload, onInstall, onSkip, onLater) }
}

@Composable
internal fun UpdateOfferContent(
    release: ReleaseInfo,
    current: String,
    download: UpdateDownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    UpdatePanel(
        icon = { Badge(colors.primaryContainer) { Text("⬇", fontSize = 28.sp, color = colors.onPrimaryContainer) } },
        title = { Text("Новая версия Pin", textAlign = TextAlign.Center) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VersionCard(
                    listOfNotNull(
                        "Сейчас" to current,
                        "Новая" to release.label(),
                        release.apkBytes?.let { "Размер" to Updates.size(it) },
                    ),
                )
                if (release.notes.isNotBlank()) {
                    Text("Что нового", style = MaterialTheme.typography.titleSmall)
                    MarkdownView(release.notes)
                }
                when (download) {
                    is UpdateDownloadState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { download.total?.let { (download.bytes.toFloat() / it).coerceIn(0f, 1f) } ?: 0.1f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Скачиваем… " + Updates.size(download.bytes) + (download.total?.let { " из " + Updates.size(it) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    is UpdateDownloadState.Ready -> Text(
                        "Скачано (" + Updates.size(download.bytes) + "). Нажмите «Установить» — откроется системный установщик, данные сохранятся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    is UpdateDownloadState.Failed -> Text(
                        "Не получилось скачать (${download.reason}). Проверьте интернет и попробуйте ещё раз.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                    UpdateDownloadState.Idle -> Text(
                        "APK скачается с GitHub в фоне; при следующем входе в приложение откроется системный установщик. Все данные сохранятся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (download) {
                is UpdateDownloadState.Downloading -> TextButton(onClick = onLater) { Text("Скрыть") }
                is UpdateDownloadState.Ready -> Button(onClick = onInstall) { Text("Установить") }
                is UpdateDownloadState.Failed -> Button(onClick = onDownload) { Text("Повторить") }
                UpdateDownloadState.Idle -> Button(onClick = onDownload) { Text("Скачать") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("Пропустить") }
                if (download !is UpdateDownloadState.Downloading) TextButton(onClick = onLater) { Text("Позже") }
            }
        },
    )
}

/** Содержимое окна обновлений в духе AlertDialog: значок, заголовок, текст, кнопки. */
@Composable
fun UpdatePanel(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Box(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) { title() }
            }
            Box(Modifier.fillMaxWidth()) { text() }
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton()
                confirmButton()
            }
        }
    }
}

/** Для скриншотов: содержимое окон без самих окон. */
@Composable
internal fun UpdateOfferPreview(release: ReleaseInfo) {
    Box(Modifier.padding(16.dp)) { UpdateOfferContent(release, "0.36", UpdateDownloadState.Idle, {}, {}, {}, {}) }
}

@Composable
internal fun UpdatePreviews(today: Long, release: ReleaseInfo) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UpdateCheckContent(UpdateCheckState.UpToDate("0.36", release.copy(version = "v0.36")), today, {}, {}, {})
        UpdateCheckContent(UpdateCheckState.Failed("GitHub временно ограничил запросы"), today, {}, {}, {})

    }
}
