package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VCardTest {
    @Test
    fun `туда и обратно без потерь`() {
        val people = listOf(
            SharedPerson("Кристина", "WIFE", "любит пионы; и сирень", 1, 4, 1997, listOf(7, 1)),
            SharedPerson("Серёга", "крёстный", "", 3, 8, null),
            SharedPerson("Без даты"),
        )
        assertEquals(people, VCard.parse(VCard.write(people)))
    }

    @Test
    fun `файл из контактов телефона читается`() {
        val text = "BEGIN:VCARD\nVERSION:2.1\nN:Иванова;Мария;;;\nitem1.BDAY;VALUE=date:19800315\nTEL:+700\nEND:VCARD\n" +
            "BEGIN:VCARD\nVERSION:3.0\nFN:Длинное\n  имя\nBDAY:--12-31\nEND:VCARD\n" +
            "BEGIN:VCARD\nVERSION:3.0\nFN:Apple\nBDAY:1604-05-06\nEND:VCARD\n"
        val people = VCard.parse(text)
        assertEquals(SharedPerson("Мария Иванова", month = 3, day = 15, year = 1980), people[0])
        assertEquals(SharedPerson("Длинное имя", month = 12, day = 31), people[1])
        assertEquals(SharedPerson("Apple", month = 5, day = 6), people[2])
        assertNull(VCard.parseBirthday("31.12"))
    }

    @Test
    fun `план импорта — новый, дописать дату, пропустить`() {
        val existing = mapOf("Мама" to true, "Семён" to false)
        val plan = PeopleImport.plan(
            existing,
            listOf(
                SharedPerson("мама", month = 3, day = 4),
                SharedPerson("Семен", month = 5, day = 6),
                SharedPerson("Кристина", month = 1, day = 4),
                SharedPerson("Кристина"),
            ),
        )
        assertEquals(listOf(ImportAction.SKIP, ImportAction.ADD_BIRTHDAY, ImportAction.ADD, ImportAction.SKIP), plan.map { it.second })
    }
}
