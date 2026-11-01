package com.sonnik.pin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentsTest {
    @Test
    fun `вложения туда и обратно`() {
        val list = listOf(Attachment("a1b2", "Счёт за май.pdf"), Attachment("c3", "фото.jpg"))
        assertEquals(list, Attachments.parse(Attachments.format(list)))
        assertNull(Attachments.format(emptyList()))
        assertEquals("attachments/a1b2/Счёт за май.pdf", list[0].path)
        assertEquals(list[0], Attachments.fromPath("Pin/attachments/a1b2/Счёт за май.pdf"))
        assertNull(Attachments.fromPath("tasks/x.md"))
    }

    @Test
    fun `опасные имена и пути`() {
        assertEquals("passwd", Attachments.safeName("../../etc/passwd"))
        assertEquals("a_b.txt", Attachments.safeName("a:b.txt"))
        assertNull(Attachments.parseOne("../x/y"))
        assertEquals(80, Attachments.safeName("я".repeat(100) + ".pdf").length)
        assertEquals("application/pdf", Attachment("1", "Скан.PDF").mime)
        assertEquals("🖼", Attachments.icon("x.jpeg"))
    }
}
