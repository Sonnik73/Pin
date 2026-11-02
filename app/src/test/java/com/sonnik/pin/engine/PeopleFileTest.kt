package com.sonnik.pin.engine

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleFileTest {
    private val anna = SharedPerson("Аня", "WIFE", "любит тюльпаны", 11, 29, 1975, listOf(7, 1))

    @Test
    fun `люди уходят в zip и читаются обратно`() {
        val bytes = PeopleFile.zip(listOf(anna, SharedPerson("Миша", month = 3, day = 8)))
        assertTrue(PeopleFile.isZip(bytes))
        val got = PeopleFile.parse(bytes, 2026).people
        assertEquals(listOf("Аня", "Миша"), got.map { it.name })
        assertEquals(1975, got.first().year)
        assertEquals("Pin — люди.zip", PeopleFile.fileName(listOf(anna, anna)))
        assertEquals("Аня.zip", PeopleFile.fileName(listOf(anna)))
    }

    @Test
    fun `zip с txt в cp1251 и простой vcf тоже читаются`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("дни рождения.txt"))
            z.write("Аня 29.11.1975\nбез даты\n".toByteArray(charset("windows-1251")))
            z.closeEntry()
            z.putNextEntry(ZipEntry("картинка.jpg"))
            z.write(byteArrayOf(1, 2, 3))
            z.closeEntry()
        }
        val parsed = PeopleFile.parse(out.toByteArray(), 2026)
        assertEquals(listOf("Аня"), parsed.people.map { it.name })
        assertEquals(listOf(2), parsed.skipped)
        val plain = PeopleFile.parse(VCard.write(listOf(anna)).toByteArray(), 2026)
        assertEquals("Аня", plain.people.single().name)
    }
}
