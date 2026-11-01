package com.sonnik.pin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.Attachment
import com.sonnik.pin.engine.Attachments

/** Вложения в редакторе: список (нажатие — открыть, × — убрать) и «Приложить файл». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentsSection(
    list: List<Attachment>,
    onAdd: ((Uri) -> Unit)?,
    onOpen: (Attachment) -> Unit,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onAdd?.invoke(it) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Вложения", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { a ->
                InputChip(
                    selected = false,
                    onClick = { onOpen(a) },
                    label = { Text(Attachments.icon(a.name) + " " + a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Text("×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onRemove(a) }.padding(horizontal = 4.dp))
                    },
                )
            }
            if (onAdd != null) {
                AssistChip(onClick = { runCatching { picker.launch(arrayOf("*/*")) } }, label = { Text("📎 Приложить файл") })
            }
        }
        if (onAdd == null) {
            Text(
                "Файлы можно приложить после сохранения задачи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Фото, PDF, документы до 10 МБ. Уезжают на другой телефон вместе с задачей (через Pin.zip).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Кнопка «📎» у задачи в списке: одно вложение — открыть сразу, несколько — выбрать из меню. */
@Composable
fun AttachmentButton(list: List<Attachment>, onOpen: (Attachment) -> Unit, modifier: Modifier = Modifier) {
    if (list.isEmpty()) return
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            if (list.size == 1) "📎 " + list.single().name else "📎 ${list.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable { if (list.size == 1) onOpen(list.single()) else menu = true }
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            list.forEach { a ->
                DropdownMenuItem(text = { Text(Attachments.icon(a.name) + " " + a.name) }, onClick = { menu = false; onOpen(a) })
            }
        }
    }
}

/** Открыть вложение — задаётся в корне приложения (через ViewModel), экраны только вызывают. */
val LocalOpenAttachment = androidx.compose.runtime.staticCompositionLocalOf<(Attachment) -> Unit> { {} }
