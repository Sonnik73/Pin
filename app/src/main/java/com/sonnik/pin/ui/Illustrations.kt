package com.sonnik.pin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Сюжет картинки для пустого экрана. */
enum class EmptyArt { TODAY, PLANS, PEOPLE, SEARCH, LISTS }

/**
 * Простые иллюстрации для пустых экранов — рисуются на Canvas в цветах темы (без картинок в ресурсах):
 * листок с булавкой, стопка планов, люди с подарком, лупа, цветные списки.
 */
@Composable
fun EmptyIllustration(art: EmptyArt, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val c = MaterialTheme.colorScheme
    val paper = c.surfaceContainerHighest
    val line = c.outlineVariant
    val accent = c.primary
    val accent2 = c.tertiary
    val soft = c.primary.copy(alpha = 0.12f)
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 100f
        // Мягкий круг-подложка.
        drawCircle(soft, radius = 46 * u, center = center)
        when (art) {
            EmptyArt.TODAY -> {
                note(Offset(30 * u, 26 * u), Size(42 * u, 50 * u), -8f, paper, line, u)
                pin(Offset(52 * u, 26 * u), u, accent, c.outline)
                // Галочка — «всё сделано».
                drawLine(accent2, Offset(38 * u, 56 * u), Offset(46 * u, 64 * u), 4 * u, StrokeCap.Round)
                drawLine(accent2, Offset(46 * u, 64 * u), Offset(62 * u, 46 * u), 4 * u, StrokeCap.Round)
            }
            EmptyArt.PLANS -> {
                note(Offset(24 * u, 32 * u), Size(40 * u, 44 * u), -12f, paper, line, u)
                note(Offset(38 * u, 24 * u), Size(40 * u, 44 * u), 6f, paper, line, u)
                star(Offset(70 * u, 26 * u), 9 * u, accent2)
            }
            EmptyArt.PEOPLE -> {
                person(Offset(38 * u, 40 * u), u, accent)
                person(Offset(60 * u, 44 * u), u * 0.85f, accent2)
                gift(Offset(48 * u, 64 * u), u, accent, paper)
            }
            EmptyArt.SEARCH -> {
                note(Offset(26 * u, 28 * u), Size(38 * u, 46 * u), -6f, paper, line, u)
                drawCircle(accent, radius = 14 * u, center = Offset(60 * u, 52 * u), style = Stroke(5 * u))
                drawLine(accent, Offset(70 * u, 62 * u), Offset(80 * u, 72 * u), 6 * u, StrokeCap.Round)
            }
            EmptyArt.LISTS -> {
                listOf(accent, accent2, c.secondary).forEachIndexed { i, color ->
                    val top = (30 + i * 16) * u
                    drawRoundRect(paper, Offset(26 * u, top), Size(48 * u, 12 * u), CornerRadius(4 * u))
                    drawCircle(color, radius = 3.5f * u, center = Offset(33 * u, top + 6 * u))
                    drawLine(line, Offset(40 * u, top + 6 * u), Offset((64 - i * 6) * u, top + 6 * u), 2.5f * u, StrokeCap.Round)
                }
            }
        }
    }
}

/** Листок со строчками, повёрнутый на [degrees]. */
private fun DrawScope.note(topLeft: Offset, size: Size, degrees: Float, paper: androidx.compose.ui.graphics.Color, line: androidx.compose.ui.graphics.Color, u: Float) {
    rotate(degrees, pivot = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)) {
        drawRoundRect(paper, topLeft, size, CornerRadius(5 * u))
        for (i in 0 until 3) {
            val y = topLeft.y + size.height * (0.38f + i * 0.17f)
            drawLine(line, Offset(topLeft.x + 6 * u, y), Offset(topLeft.x + size.width - (6 + i * 5) * u, y), 2.5f * u, StrokeCap.Round)
        }
    }
}

/** Булавка: шляпка и игла. */
private fun DrawScope.pin(top: Offset, u: Float, head: androidx.compose.ui.graphics.Color, needle: androidx.compose.ui.graphics.Color) {
    drawLine(needle, Offset(top.x, top.y + 6 * u), Offset(top.x + 4 * u, top.y + 18 * u), 2.2f * u, StrokeCap.Round)
    drawCircle(head, radius = 7 * u, center = top)
    drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), radius = 2.2f * u, center = Offset(top.x - 2.5f * u, top.y - 2.5f * u))
}

private fun DrawScope.star(center: Offset, r: Float, color: androidx.compose.ui.graphics.Color) {
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((i * 36 - 90).toDouble())
        val p = Offset(center.x + (rad * kotlin.math.cos(a)).toFloat(), center.y + (rad * kotlin.math.sin(a)).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.person(head: Offset, u: Float, color: androidx.compose.ui.graphics.Color) {
    drawCircle(color, radius = 8 * u, center = head)
    drawRoundRect(color, Offset(head.x - 12 * u, head.y + 11 * u), Size(24 * u, 16 * u), CornerRadius(10 * u))
}

private fun DrawScope.gift(center: Offset, u: Float, ribbon: androidx.compose.ui.graphics.Color, box: androidx.compose.ui.graphics.Color) {
    val w = 22 * u
    drawRoundRect(box, Offset(center.x - w / 2, center.y - 6 * u), Size(w, 16 * u), CornerRadius(3 * u))
    drawRect(ribbon, Offset(center.x - 2 * u, center.y - 6 * u), Size(4 * u, 16 * u))
    drawCircle(ribbon, radius = 3 * u, center = Offset(center.x - 4 * u, center.y - 8 * u))
    drawCircle(ribbon, radius = 3 * u, center = Offset(center.x + 4 * u, center.y - 8 * u))
}
