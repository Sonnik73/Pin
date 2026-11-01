package com.sonnik.pin.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    @Test
    fun `разбор разделов и пунктов`() {
        val text = "# Изменения\n\n## 0.2\n\n- Первое\n- Второе,\n  продолжение\n\n## 0.1\n\n- Начало\n"
        assertEquals(
            listOf(ChangelogEntry("0.2", listOf("Первое", "Второе, продолжение")), ChangelogEntry("0.1", listOf("Начало"))),
            Changelog.parse(text),
        )
    }

    @Test
    fun `настоящий журнал начинается с текущей версии`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "gradle.properties").exists() && File(it, "changelog").exists() }
        val version = File(root, "gradle.properties").readLines().first { it.startsWith("appVersion=") }.substringAfter('=')
        for (name in listOf("changelog_ru.md", "changelog_en.md")) {
            val entries = Changelog.parse(File(root, "changelog/$name").readText())
            assertEquals("$name: первая версия", version, entries.first().version)
            assertTrue(entries.all { it.items.isNotEmpty() })
        }
    }
}
