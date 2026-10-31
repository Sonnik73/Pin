package com.sonnik.pin.engine

import com.sonnik.pin.engine.Markdown.Block
import com.sonnik.pin.engine.Markdown.Inline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun `блоки`() {
        val text = """
            # Покупки
            Купить **хлеб** и *молоко*,
            а ещё _сыр_.

            - [ ] хлеб
            - [x] молоко
              - [X] вложенный
            - обычный пункт
            1. первый
            > цитата
            ---
            ```
            код **не разбирается**
            ```
        """.trimIndent()
        val blocks = Markdown.parse(text)
        assertEquals(Block.Heading(1, listOf(Inline.Plain("Покупки"))), blocks[0])
        assertEquals(
            Block.Paragraph(
                listOf(
                    Inline.Plain("Купить "), Inline.Bold("хлеб"), Inline.Plain(" и "), Inline.Italic("молоко"),
                    Inline.Plain(", а ещё "), Inline.Italic("сыр"), Inline.Plain("."),
                ),
            ),
            blocks[1],
        )
        assertEquals(Block.Task(0, false, listOf(Inline.Plain("хлеб")), 4), blocks[2])
        assertEquals(Block.Task(0, true, listOf(Inline.Plain("молоко")), 5), blocks[3])
        assertEquals(Block.Task(1, true, listOf(Inline.Plain("вложенный")), 6), blocks[4])
        assertEquals(Block.Item(0, "•", listOf(Inline.Plain("обычный пункт"))), blocks[5])
        assertEquals(Block.Item(0, "1.", listOf(Inline.Plain("первый"))), blocks[6])
        assertTrue(blocks[7] is Block.Quote)
        assertEquals(Block.Rule, blocks[8])
        assertEquals(Block.Code("код **не разбирается**"), blocks[9])
    }

    @Test
    fun `ссылки и код`() {
        assertEquals(
            listOf(Inline.Plain("см. "), Inline.Link("сайт", "https://ya.ru"), Inline.Plain(" и "), Inline.Link("https://x.org/a", "https://x.org/a"), Inline.Plain(".")),
            Markdown.inline("см. [сайт](https://ya.ru) и https://x.org/a."),
        )
        assertEquals(listOf(Inline.Code("a*b*c")), Markdown.inline("`a*b*c`"))
        assertEquals(listOf(Inline.Plain("2 * 3 * 4")), Markdown.inline("2 * 3 * 4"))
        assertEquals(listOf(Inline.Plain("snake_case_name")), Markdown.inline("snake_case_name"))
        assertEquals(listOf(Inline.BoldItalic("всё"), Inline.Plain(" "), Inline.Strike("нет")), Markdown.inline("***всё*** ~~нет~~"))
    }

    @Test
    fun `чекбокс переключается в исходном тексте`() {
        val text = "Список:\n- [ ] хлеб\n  * [x] молоко\nконец"
        val once = Markdown.toggleTask(text, 1)
        assertEquals("Список:\n- [x] хлеб\n  * [x] молоко\nконец", once)
        assertEquals("Список:\n- [x] хлеб\n  * [ ] молоко\nконец", Markdown.toggleTask(once, 2))
        assertEquals(text, Markdown.toggleTask(text, 0))
        assertEquals(text, Markdown.toggleTask(text, 99))
        assertEquals(2 to 2, Markdown.checklist(Markdown.toggleTask(text, 1)))
        assertEquals(1 to 2, Markdown.checklist(text))
        assertEquals(0 to 0, Markdown.checklist("нет списка"))
    }
}
