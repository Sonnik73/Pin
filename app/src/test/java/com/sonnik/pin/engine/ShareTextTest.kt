package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
    @Test
    fun `тема и ссылка`() {
        assertEquals(
            SharedTask("Как выбрать электромотоцикл", "https://habr.com/ru/articles/1/"),
            ShareText.parse("Как выбрать электромотоцикл", "https://habr.com/ru/articles/1/"),
        )
    }

    @Test
    fun `текст со ссылкой без темы`() {
        assertEquals(
            SharedTask("завтра купить фильтр", "смотри тут\nhttps://ozon.ru/t/abc"),
            ShareText.parse(null, "завтра купить фильтр\nсмотри тут https://ozon.ru/t/abc."),
        )
    }

    @Test
    fun `только ссылка и только текст`() {
        assertEquals(SharedTask("Посмотреть: youtube.com", "https://www.youtube.com/watch?v=1"), ShareText.parse("", "https://www.youtube.com/watch?v=1"))
        assertEquals(SharedTask("Позвонить в сервис", ""), ShareText.parse(null, "  Позвонить в сервис  "))
        assertEquals(SharedTask("", ""), ShareText.parse(null, null))
    }
}
