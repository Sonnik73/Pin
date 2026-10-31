package com.sonnik.pin.ui

import android.os.Bundle
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
fun QuickAddField(today: Long, onAdd: (QuickDraft) -> Unit, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    var text by rememberSaveable { mutableStateOf("") }
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
        placeholder = { Text("Быстро: «завтра в 18 позвонить маме»") },
        supportingText = draft?.let { d -> { Text(if (d.title.isBlank()) "Добавьте, что сделать" else "«${d.title}» — ${QuickAdd.describe(d, today)}") } },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = ::submit, enabled = draft?.title?.isNotBlank() == true) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Добавить")
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
                                Text("📌 Новая задача", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                QuickAddField(
                                    today = today,
                                    autoFocus = true,
                                    onAdd = { d ->
                                        lifecycleScope.launch {
                                            repo.create(d.toTaskDraft())
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
