package com.sonnik.pin.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.Tags

/** Показывает цифры даты с точками: «0503» → «05.03». В состоянии поля хранятся только цифры. */
private object DateDotsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = RuText.dateFromDigits(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(shown.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** Только цифры из даты «05.03.1996» → «05031996». */
fun dateDigits(text: String): String = text.filter(Char::isDigit)

/**
 * Поле даты: вводятся только цифры, точки ставятся сами. [withYear] — разрешить год (ДД.ММ.ГГГГ), иначе ДД.ММ.
 * [digits] — цифры без точек; полная дата для разбора — `RuText.dateFromDigits(digits)`.
 */
@Composable
fun DateField(
    digits: String,
    onDigits: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    withYear: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = digits,
        onValueChange = { v -> onDigits(v.filter(Char::isDigit).take(if (withYear) 8 else 4)) },
        label = { Text(label) },
        placeholder = { Text(if (withYear) "ДД.ММ.ГГГГ" else "ДД.ММ") },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        visualTransformation = DateDotsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Поле тегов через запятую с выпадающим списком уже известных тегов, совпадающих с вводом. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagField(value: String, onValue: (String) -> Unit, allTags: Collection<String>, modifier: Modifier = Modifier) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val suggestions = remember(value, allTags) { Tags.suggest(allTags, value) }
    val expanded = suggestions.isNotEmpty() && !dismissed
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { dismissed = !it }, modifier = modifier) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                dismissed = false
                onValue(it.text)
            },
            label = { Text("Теги через запятую") },
            placeholder = { Text("дом, работа") },
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { dismissed = true }) {
            suggestions.forEach { tag ->
                DropdownMenuItem(
                    text = { Text("#$tag") },
                    onClick = {
                        val next = Tags.apply(value, tag)
                        field = TextFieldValue(next, TextRange(next.length))
                        onValue(next)
                    },
                )
            }
        }
    }
}

/** Обычный текст — с заглавной буквы. */
val SENTENCES = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences)
