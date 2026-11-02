package com.sonnik.pin.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Файл «Поделиться людьми»: .zip с .vcf внутри — такой Telegram и другие мессенджеры пересылают как обычный файл
 * (голый .vcf они превращают в карточку контакта, и дни рождения теряются).
 * Читаем терпимо: .zip с .vcf или .txt внутри, просто .vcf или .txt (UTF-8 или «ANSI» Блокнота).
 */
object PeopleFile {
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024

    /** Имя файла для отправки: один человек — по имени, несколько — общее. */
    fun fileName(people: List<SharedPerson>): String =
        (if (people.size == 1) MdDoc.fileName(people.single().name) else "Pin — люди") + ".zip"

    fun zip(people: List<SharedPerson>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(fileName(people).removeSuffix(".zip") + ".vcf"))
            zip.write(VCard.write(people).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** Тексты из файла: содержимое .vcf/.txt внутри .zip или сам файл. */
    fun texts(bytes: ByteArray): List<String> {
        if (!isZip(bytes)) return listOf(decode(bytes))
        val result = ArrayList<String>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.lowercase()
                    if (e.isDirectory || name.contains("__macosx")) continue
                    if (!(name.endsWith(".vcf") || name.endsWith(".vcard") || name.endsWith(".txt"))) continue
                    val data = readLimited(zip) ?: continue
                    result += decode(data)
                }
            }
        }
        return result
    }

    /** Люди из файла и номера строк .txt без даты (их пропускаем и называем после импорта). */
    fun parse(bytes: ByteArray, currentYear: Int): BirthdayText.Result {
        val people = ArrayList<SharedPerson>()
        val skipped = ArrayList<Int>()
        for (text in texts(bytes)) {
            val vcard = VCard.parse(text)
            if (vcard.isNotEmpty()) {
                people += vcard
            } else {
                val parsed = BirthdayText.parse(text, currentYear)
                people += parsed.people
                skipped += parsed.skipped
            }
        }
        return BirthdayText.Result(people, skipped)
    }

    /** UTF-8, а если не читается — cp1251 (Блокнот Windows «ANSI»). */
    fun decode(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("﻿")
        return if ('�' in utf8) bytes.toString(Charset.forName("windows-1251")) else utf8
    }

    private fun readLimited(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_ENTRY_BYTES) return null
        }
        return out.toByteArray()
    }
}
