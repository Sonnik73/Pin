package com.sonnik.pin.engine

/**
 * Небольшой разбор markdown для предпросмотра: заголовки, списки, чекбоксы, цитаты, код, жирный/курсив, ссылки.
 * Не стремится к полной CommonMark — только то, что пишут в заметках к задачам. Отрисовка — в Compose.
 */
object Markdown {
    sealed interface Inline {
        val text: String

        data class Plain(override val text: String) : Inline
        data class Bold(override val text: String) : Inline
        data class Italic(override val text: String) : Inline
        data class BoldItalic(override val text: String) : Inline
        data class Strike(override val text: String) : Inline
        data class Code(override val text: String) : Inline
        data class Link(override val text: String, val url: String) : Inline
    }

    sealed interface Block {
        data class Heading(val level: Int, val inlines: List<Inline>) : Block
        data class Paragraph(val inlines: List<Inline>) : Block
        /** Пункт списка; [marker] — «•» или «1.». [indent] — уровень вложенности. */
        data class Item(val indent: Int, val marker: String, val inlines: List<Inline>) : Block
        /** Чекбокс `- [ ]` / `- [x]`; [line] — номер строки в исходном тексте, чтобы отметить его. */
        data class Task(val indent: Int, val checked: Boolean, val inlines: List<Inline>, val line: Int) : Block
        data class Quote(val inlines: List<Inline>) : Block
        data class Code(val text: String) : Block
        data object Rule : Block
    }

    private val TASK = Regex("""^(\s*)[-*+]\s+\[([ xXхХ])]\s?(.*)$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

    fun parse(text: String): List<Block> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(inline(paragraph.toString()))
                paragraph.clear()
            }
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                blocks += Block.Code(code.toString())
                i++
                continue
            }
            val task = TASK.matchEntire(line)
            val heading = HEADING.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            val ordered = ORDERED.matchEntire(line)
            when {
                line.isBlank() -> flush()
                task != null -> {
                    flush()
                    val (indent, mark, rest) = task.destructured
                    blocks += Block.Task(level(indent), mark != " ", inline(rest), i)
                }
                RULE.matches(line) -> { flush(); blocks += Block.Rule }
                heading != null -> {
                    flush()
                    blocks += Block.Heading(heading.groupValues[1].length, inline(heading.groupValues[2]))
                }
                bullet != null -> {
                    flush()
                    blocks += Block.Item(level(bullet.groupValues[1]), "•", inline(bullet.groupValues[2]))
                }
                ordered != null -> {
                    flush()
                    blocks += Block.Item(level(ordered.groupValues[1]), ordered.groupValues[2] + ".", inline(ordered.groupValues[3]))
                }
                line.trimStart().startsWith(">") -> {
                    flush()
                    blocks += Block.Quote(inline(line.trimStart().removePrefix(">").trimStart()))
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
            i++
        }
        flush()
        return blocks
    }

    private fun level(indent: String): Int = indent.replace("\t", "    ").length / 2

    private val INLINE = Regex(
        """\*\*\*(.+?)\*\*\*""" + // 1 жирный курсив
            """|\*\*(.+?)\*\*|__(.+?)__""" + // 2, 3 жирный
            """|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""" + // 4, 5 курсив
            """|~~(.+?)~~""" + // 6 зачёркнутый
            """|`([^`]+)`""" + // 7 код
            """|\[([^\]]+)]\(([^)\s]+)\)""" + // 8, 9 ссылка
            """|(https?://[^\s<>()]+[^\s<>().,;:!?'"»])""", // 10 голая ссылка
    )

    fun inline(text: String): List<Inline> {
        val out = ArrayList<Inline>()
        var pos = 0
        for (m in INLINE.findAll(text)) {
            if (m.range.first > pos) out += Inline.Plain(text.substring(pos, m.range.first))
            val g = m.groupValues
            out += when {
                g[1].isNotEmpty() -> Inline.BoldItalic(g[1])
                g[2].isNotEmpty() -> Inline.Bold(g[2])
                g[3].isNotEmpty() -> Inline.Bold(g[3])
                g[4].isNotEmpty() -> Inline.Italic(g[4])
                g[5].isNotEmpty() -> Inline.Italic(g[5])
                g[6].isNotEmpty() -> Inline.Strike(g[6])
                g[7].isNotEmpty() -> Inline.Code(g[7])
                g[8].isNotEmpty() -> Inline.Link(g[8], g[9])
                else -> Inline.Link(g[10], g[10])
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) out += Inline.Plain(text.substring(pos))
        return out
    }

    /** Переключить чекбокс в строке [line] исходного текста. Если там не чекбокс — текст не меняется. */
    fun toggleTask(text: String, line: Int): String {
        val lines = text.split('\n').toMutableList()
        val m = TASK.matchEntire(lines.getOrNull(line)?.removeSuffix("\r") ?: return text) ?: return text
        val markIndex = m.groups[2]!!.range.first
        val current = lines[line]
        val next = if (m.groupValues[2] == " ") 'x' else ' '
        lines[line] = current.substring(0, markIndex) + next + current.substring(markIndex + 1)
        return lines.joinToString("\n")
    }

    /** Сколько подпунктов отмечено и сколько всего — для подписи «☑ 2/5». */
    fun checklist(text: String): Pair<Int, Int> {
        var done = 0
        var total = 0
        for (line in text.split('\n')) {
            val m = TASK.matchEntire(line.removeSuffix("\r")) ?: continue
            total++
            if (m.groupValues[2] != " ") done++
        }
        return done to total
    }
}
