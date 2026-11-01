package com.sonnik.pin.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.Markdown

/** Вставка разметки у курсора: в начало строки (списки, заголовки) или вокруг выделения (жирный, курсив). */
private fun TextFieldValue.linePrefix(prefix: String): TextFieldValue {
    val start = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)).let { if (selection.start == 0) 0 else it + 1 }
    val newText = text.substring(0, start) + prefix + text.substring(start)
    return copy(text = newText, selection = TextRange(selection.start + prefix.length, selection.end + prefix.length))
}

private fun TextFieldValue.wrap(left: String, right: String, placeholder: String): TextFieldValue {
    val sel = selection
    val inner = if (sel.collapsed) placeholder else text.substring(sel.min, sel.max)
    val newText = text.substring(0, sel.min) + left + inner + right + text.substring(sel.max)
    return copy(text = newText, selection = TextRange(sel.min + left.length, sel.min + left.length + inner.length))
}

/** Описание в markdown: правка с панелью разметки и предпросмотр, где чекбоксы отмечаются нажатием. */
@Composable
fun MarkdownField(body: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "Описание") {
    var preview by rememberSaveable { mutableStateOf(body.isNotBlank()) }
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(body)) }
    if (value.text != body) value = value.copy(text = body, selection = TextRange(body.length))
    fun update(v: TextFieldValue) {
        value = v
        onChange(v.text)
    }

    // Набор с клавиатуры: Enter после пункта списка продолжает список.
    fun typed(v: TextFieldValue) {
        val cont = if (v.selection.collapsed) Markdown.continueList(value.text, v.text, v.selection.start) else null
        update(if (cont == null) v else v.copy(text = cont.first, selection = TextRange(cont.second)))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 8.dp))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = !preview, onClick = { preview = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Текст") }
                SegmentedButton(selected = preview, onClick = { preview = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Просмотр") }
            }
        }
        if (preview) {
            MarkdownView(
                text = body,
                onToggle = { line -> update(TextFieldValue(Markdown.toggleTask(body, line))) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .clickable(enabled = body.isBlank()) { preview = false }
                    .padding(12.dp),
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ToolButton("☐ пункт") { update(value.linePrefix("- [ ] ")) }
                ToolButton("• список") { update(value.linePrefix("- ")) }
                ToolButton("Заголовок") { update(value.linePrefix("## ")) }
                ToolButton("Ж", bold = true) { update(value.wrap("**", "**", "жирный")) }
                ToolButton("К") { update(value.wrap("*", "*", "курсив")) }
                ToolButton("Ссылка") { update(value.wrap("[", "](https://)", "текст")) }
            }
            OutlinedTextField(
                value = value,
                onValueChange = ::typed,
                placeholder = { Text("- [ ] подпункт\n**жирный**, *курсив*, [ссылка](https://…)") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

@Composable
private fun ToolButton(label: String, bold: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, fontWeight = if (bold) FontWeight.Bold else null) }
}
