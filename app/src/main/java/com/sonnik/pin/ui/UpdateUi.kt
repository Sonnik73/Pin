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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

/** Круглый значок над заголовком окна. */
@Composable
private fun Badge(background: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(64.dp).background(background, CircleShape), contentAlignment = Alignment.Center) { content() }
}

/** Карточка «что установлено / что на GitHub». */
@Composable
private fun VersionCard(rows: List<Pair<String, String>>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
@Composable
fun UpdateCheckDialog(state: UpdateCheckState, today: Long, onRetry: () -> Unit, onChangelog: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
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

/** «Новая версия Pin»: было → стало, дата и размер, что нового, кнопка «Скачать». */
@Composable
fun UpdateDialog(release: ReleaseInfo, current: String, onDownload: () -> Unit, onSkip: () -> Unit, onLater: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onLater,
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
                Text(
                    "APK скачается с GitHub — откройте его и подтвердите установку поверх текущей версии. Все данные сохранятся.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onDownload) { Text("Скачать") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("Пропустить") }
                TextButton(onClick = onLater) { Text("Позже") }
            }
        },
    )
}
