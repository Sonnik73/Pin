package com.sonnik.pin.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import com.sonnik.pin.engine.ShareText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sonnik.pin.PinApp
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.QuickAdd
import com.sonnik.pin.engine.QuickDraft
import kotlinx.coroutines.launch

fun QuickDraft.toTaskDraft() = TaskDraft(title = title, day = day, timeMinutes = timeMinutes, priority = priority, tags = tags)

/** Строка быстрого добавления с подсказкой, как она понята. */
@Composable
fun QuickAddField(
    today: Long,
    onAdd: (QuickDraft) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    initialText: String = "",
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val context = LocalContext.current
    // Голосовой ввод: распознавание речи Android (по возможности без интернета); результат — в строку для проверки.
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
            text = heard.replaceFirstChar { it.uppercase() }
        }
    }
    val draft = remember(text, today) { if (text.isBlank()) null else QuickAdd.parse(text, today) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        val d = draft ?: return
        if (d.title.isBlank()) return
        onAdd(d)
        text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Быстро: «завтра в 18 позвонить»", maxLines = 1) },
        supportingText = draft?.let { d -> { Text(if (d.title.isBlank()) "Добавьте, что сделать" else "«${d.title}» — ${QuickAdd.describe(d, today)}") } },
        singleLine = true,
        trailingIcon = {
            Row {
                if (speechAvailable && text.isEmpty()) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Например: «завтра в шесть позвонить маме»")
                        runCatching { speech.launch(intent) }
                    }) {
                        Icon(PinIcons.Mic, contentDescription = "Сказать голосом")
                    }
                }
                IconButton(onClick = ::submit, enabled = draft?.title?.isNotBlank() == true) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Добавить")
                }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = modifier.focusRequester(focus),
    )
}

/** Окно быстрого добавления поверх рабочего стола (кнопка «+» в виджете). */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as PinApp).repository
        // «Поделиться → Pin»: тема и текст из другого приложения; ссылки и остальное — в описание задачи.
        val shared = if (intent?.action == Intent.ACTION_SEND) {
            ShareText.parse(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            null
        }
        var today by mutableLongStateOf(Long.MIN_VALUE)
        lifecycleScope.launch { today = repo.logicalDay().today() }
        setContent {
            PinTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (today != Long.MIN_VALUE) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    if (shared != null) "📌 Задача из «Поделиться»" else "📌 Новая задача",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                QuickAddField(
                                    today = today,
                                    autoFocus = true,
                                    initialText = shared?.line.orEmpty(),
                                    onAdd = { d ->
                                        lifecycleScope.launch {
                                            repo.create(d.toTaskDraft().copy(body = shared?.body.orEmpty()))
                                            if (shared != null) {
                                                android.widget.Toast.makeText(this@QuickAddActivity, "Добавлено в Pin", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (!shared?.body.isNullOrBlank()) {
                                    Text(
                                        "В описание: " + shared!!.body.replace("\n", " · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
