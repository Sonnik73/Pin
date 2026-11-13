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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sonnik.pin.PinApp
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.QuickAdd
import com.sonnik.pin.engine.QuickAdd.QuickMarkKind
import com.sonnik.pin.engine.QuickDraft
import com.sonnik.pin.engine.RuText
import kotlinx.coroutines.launch

fun QuickDraft.toTaskDraft() = TaskDraft(title = title, day = day, timeMinutes = timeMinutes, priority = priority, tags = tags)

/** Подсветка распознанного в поле: дата/время/«!» — акцентом, теги и список — вторым акцентом. */
private class QuickAddHighlight(marks: List<com.sonnik.pin.engine.QuickAdd.QuickMark>, accent: androidx.compose.ui.graphics.Color, second: androidx.compose.ui.graphics.Color) :
    VisualTransformation {
    private val spans = marks.map { m ->
        Triple(
            m.start,
            m.end,
            when (m.kind) {
                QuickMarkKind.TAG, QuickMarkKind.LIST -> SpanStyle(color = second, fontWeight = FontWeight.Medium)
                else -> SpanStyle(color = accent, fontWeight = FontWeight.Medium)
            },
        )
    }

    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        buildAnnotatedString {
            append(text.text)
            spans.forEach { (start, end, style) -> addStyle(style, start, end.coerceAtMost(text.length)) }
        },
        OffsetMapping.Identity,
    )
}

/** Правки атрибутов чипами поверх разобранной строки; сбрасываются, когда текст изменили. */
private data class QuickOverrides(val day: Long? = null, val time: Int? = null, val hasTime: Boolean = false, val priority: com.sonnik.pin.engine.Priority? = null)

/** Пример-чип для пустого поля: тап подставляет пример в ввод. */
@Composable
private fun ExampleChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Чип атрибута: тап — поправить (дата, время, важность), без действия — просто показать (тег, список). */
@Composable
private fun AttrChip(label: String, active: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Строка быстрого добавления: распознанное подсвечивается, атрибуты — чипами, тап поправит их. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val parsed = remember(text, today) { if (text.isBlank()) null else QuickAdd.parse(text, today) }
    // Правки чипами живут до следующей правки текста: изменил строку — разбор заново.
    var overrides by remember(text, today) { mutableStateOf(QuickOverrides()) }
    val draft = parsed?.let { p ->
        p.copy(
            day = overrides.day ?: p.day,
            timeMinutes = if (overrides.hasTime) overrides.time else p.timeMinutes,
            priority = overrides.priority ?: p.priority,
        )
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val highlight = remember(parsed, colors.primary, colors.tertiary) {
        parsed?.let { QuickAddHighlight(QuickAdd.marks(text, it), colors.primary, colors.tertiary) } ?: VisualTransformation.None
    }
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
        placeholder = { Text("Быстро: «завтра в 18 позвонить»", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        supportingText = {
            when {
                // Пустое поле — примеры одним тапом: видно, что вообще можно писать.
                draft == null && text.isBlank() -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("завтра в 9 тренировка", "в пятницу отчёт #работа", "! срочно позвонить").forEach { ex ->
                        ExampleChip(ex) { text = ex }
                    }
                }
                draft != null && draft.title.isBlank() -> Text("Добавьте, что сделать")
                draft != null -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttrChip(RuText.relativeDay(draft.day, today), active = overrides.day != null, onClick = { pickDate = true })
                    if (draft.timeMinutes != null) {
                        AttrChip(RuText.time(draft.timeMinutes), active = overrides.hasTime, onClick = { pickTime = true })
                    } else {
                        AttrChip("+ время", onClick = { pickTime = true })
                    }
                    // «важная» выключается тапом, «+ важно» — включает; правка сбрасывается к тому, что в строке.
                    if (draft.priority == com.sonnik.pin.engine.Priority.HIGH) {
                        AttrChip("важная", active = true, onClick = { overrides = overrides.copy(priority = parsed.priority) })
                    } else {
                        AttrChip("+ важно", onClick = { overrides = overrides.copy(priority = com.sonnik.pin.engine.Priority.HIGH) })
                    }
                    draft.tags.forEach { AttrChip("#$it") }
                    draft.list?.let { AttrChip("@$it") }
                }
            }
        },
        singleLine = true,
        visualTransformation = highlight,
        trailingIcon = {
            Row {
                if (speechAvailable && text.isEmpty() && com.sonnik.pin.engine.Feature.VOICE.on()) {
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

    if (pickDate && draft != null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.day * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { overrides = overrides.copy(day = Math.floorDiv(it, DAY_MILLIS)) }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
    if (pickTime) {
        val initial = draft?.timeMinutes ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Время") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { overrides = overrides.copy(time = state.hour * 60 + state.minute, hasTime = true); pickTime = false }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { overrides = overrides.copy(time = null, hasTime = true); pickTime = false }) { Text("Без времени") }
            },
        )
    }
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
        val disabledFeatures = (application as PinApp).featureSettings.disabled.value
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalDisabledFeatures provides disabledFeatures) {
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
                                            val listId = d.list?.let { repo.listIdFor(it) }
                                            repo.create(d.toTaskDraft().copy(body = shared?.body.orEmpty(), listId = listId))
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
}
