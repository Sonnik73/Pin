package com.sonnik.pin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.Moments

/** Цвета групп важных дат: середина яркости — читаются и в светлой, и в тёмной теме. */
internal val GROUP_PALETTE = listOf(
    Color(0xFF5B9BFF), Color(0xFF34B97C), Color(0xFFE5A22E), Color(0xFFEE6699), Color(0xFF9B7CFF), Color(0xFF26B3B3),
)

internal fun groupColor(name: String): Color = GROUP_PALETTE[Moments.groupColor(name)]

@Composable
internal fun GroupDot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

/** Метка группы на карточке: точка и название на светлой подложке её цвета. */
@Composable
private fun GroupChip(name: String) {
    val c = groupColor(name)
    Surface(color = c.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupDot(c, 6)
            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Итог группы плитками: «11 записей», «1073 дня в периодах», «278 в 2026 году». */
@Composable
internal fun GroupSummaryCard(group: String, summary: Moments.Summary, modifier: Modifier = Modifier) {
    val c = groupColor(group)
    val surface = MaterialTheme.colorScheme.surface
    Surface(color = c.copy(alpha = 0.14f).compositeOver(surface), shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PinIcons.Folder, null, tint = c, modifier = Modifier.size(18.dp))
                Text(group, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Moments.tiles(summary).forEach { (number, label) ->
                    Surface(color = c.copy(alpha = 0.12f).compositeOver(surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(number, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Карточка важной даты: название, даты, группа; крупная цифра и полоска периода. */
@Composable
internal fun MomentCard(m: EntryEntity, today: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val day = m.momentDay ?: return
    val big = Moments.big(day, m.momentEnd, m.momentOngoing, today)
    val colors = MaterialTheme.colorScheme
    val active = big.phase == Moments.Phase.NOW || big.phase == Moments.Phase.TODAY
    Surface(onClick = onClick, color = colors.surfaceContainer, shape = RoundedCornerShape(22.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(PinIcons.Place, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(
                            Moments.describe(day, m.momentEnd, m.momentOngoing).substringBefore(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (m.momentRemind != null) Icon(PinIcons.Bell, "С напоминанием", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
                m.momentGroup?.let { GroupChip(it) }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val numeric = big.number.all(Char::isDigit)
                Text(
                    big.number,
                    fontSize = if (numeric) 42.sp else 28.sp,
                    lineHeight = if (numeric) 42.sp else 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    color = if (active) colors.primary else colors.onSurface,
                )
                Column(Modifier.weight(1f).padding(bottom = 2.dp)) {
                    if (big.unit.isNotEmpty()) Text(big.unit, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(big.caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (big.phase == Moments.Phase.NOW) {
                    Surface(color = colors.primary, shape = RoundedCornerShape(50)) {
                        Text("сейчас", style = MaterialTheme.typography.labelMedium, color = colors.onPrimary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                    }
                }
            }
            big.progress?.let { ProgressLine(it, if (active) colors.primary else colors.outline, big.open) }
            val note = m.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(100)
            val tags = m.tagList.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
            listOfNotNull(note, tags).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Полоска периода; у «по настоящее время» уходит в точки — конца ещё нет. */
@Composable
private fun ProgressLine(fraction: Float, color: Color, open: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        if (open) listOf(0.6f, 0.35f, 0.15f).forEach { a -> Box(Modifier.size(6.dp).background(color.copy(alpha = a), CircleShape)) }
    }
    Spacer(Modifier.height(0.dp))
}
