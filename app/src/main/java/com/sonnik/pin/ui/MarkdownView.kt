package com.sonnik.pin.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.Markdown
import com.sonnik.pin.engine.Markdown.Block
import com.sonnik.pin.engine.Markdown.Inline

/** Предпросмотр markdown. Чекбоксы нажимаются: [onToggle] получает номер строки исходного текста. */
@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier, onToggle: ((Int) -> Unit)? = null) {
    val blocks = remember(text) { Markdown.parse(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (blocks.isEmpty()) {
            Text("Пусто", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (block in blocks) {
            when (block) {
                is Block.Heading -> Text(
                    annotated(block.inlines, linkColor, codeBg),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
                is Block.Paragraph -> Text(annotated(block.inlines, linkColor, codeBg), style = MaterialTheme.typography.bodyLarge)
                is Block.Item -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                    Text(block.marker, modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(annotated(block.inlines, linkColor, codeBg), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Task -> Row(
                    Modifier.padding(start = (block.indent * 16).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = onToggle?.let { toggle -> { _: Boolean -> toggle(block.line) } },
                        )
                    }
                    Text(
                        annotated(block.inlines, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                        color = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                }
                is Block.Quote -> Row {
                    Box(Modifier.width(3.dp).size(3.dp, 22.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Text(
                        annotated(block.inlines, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                is Block.Code -> Text(
                    block.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().background(codeBg, RoundedCornerShape(8.dp)).padding(10.dp),
                )
                Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

private fun annotated(inlines: List<Inline>, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    for (i in inlines) {
        when (i) {
            is Inline.Plain -> append(i.text)
            is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(i.text) }
            is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(i.text) }
            is Inline.BoldItalic -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(i.text) }
            is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(i.text) }
            is Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(i.text) }
            is Inline.Link -> withLink(
                LinkAnnotation.Url(i.url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))),
            ) { append(i.text) }
        }
    }
}

/**
 * Кнопка «раскрыть» под задачей: подпункты, ссылки и текст описания прямо в списке,
 * подпункты отмечаются без открытия задачи.
 */
@Composable
fun BodyToggle(body: String, expanded: Boolean, onExpand: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (body.isBlank()) return
    val (done, total) = remember(body) { Markdown.checklist(body) }
    val label = when {
        total > 0 -> "☑ $done/$total"
        Markdown.parse(body).any { b -> b is Block.Item } -> "список"
        else -> "описание"
    }
    Text(
        (if (expanded) "▴ " else "▾ ") + label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onExpand(!expanded) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** Раскрытое описание под задачей. */
@Composable
fun ExpandedBody(body: String, onBodyChange: (String) -> Unit, modifier: Modifier = Modifier) {
    MarkdownView(
        body,
        modifier = modifier,
        onToggle = { line -> onBodyChange(Markdown.toggleTask(body, line)) },
    )
}
