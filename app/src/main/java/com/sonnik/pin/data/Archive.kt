package com.sonnik.pin.data

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Zip-архив папки «Pin»: резервная копия, которая работает без Google. */
object Archive {
    const val ROOT = "Pin"
    private const val FIXED_TIME = 1_577_836_800_000L // 2020-01-01

    fun toBytes(files: List<MdFiles.File>): ByteArray =
        java.io.ByteArrayOutputStream().also { write(it, files) }.toByteArray()

    fun toBytes(files: List<MdFiles.File>, blobs: List<Pair<String, ByteArray>>): ByteArray =
        java.io.ByteArrayOutputStream().also { write(it, files, blobs) }.toByteArray()

    /** [blobs] — вложения: путь вида «attachments/<id>/<имя>» и содержимое. */
    fun write(out: OutputStream, files: List<MdFiles.File>, blobs: List<Pair<String, ByteArray>> = emptyList()) {
        ZipOutputStream(out).use { zip ->
            for (f in files) {
                // Постоянное время файлов: одинаковые данные дают байт-в-байт одинаковый архив (не перезаписываем зря).
                zip.putNextEntry(ZipEntry("$ROOT/${f.path}").apply { time = FIXED_TIME })
                zip.write(f.text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            for ((path, bytes) in blobs.sortedBy { it.first }) {
                zip.putNextEntry(ZipEntry("$ROOT/$path").apply { time = FIXED_TIME })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** Содержимое архива: .md-файлы и вложения. */
    data class Content(val files: List<Pair<String, String>>, val blobs: List<Pair<String, ByteArray>>)

    /** .md и вложения (attachments/…, до [maxBlobBytes] каждое) за один проход. */
    fun readContent(input: InputStream, maxFileBytes: Int = 2 * 1024 * 1024, maxBlobBytes: Int = com.sonnik.pin.engine.Attachments.MAX_BYTES): Content {
        val md = ArrayList<Pair<String, String>>()
        val blobs = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory || name.contains("../")) continue
                // «Pin/attachments/<id>/<имя>» или «attachments/<id>/<имя>» — вложение.
                val at = name.indexOf("attachments/")
                val attachment = if (at == 0 || (at > 0 && name[at - 1] == '/')) {
                    com.sonnik.pin.engine.Attachments.fromPath(name.substring(at))
                } else {
                    null
                }
                when {
                    attachment != null -> {
                        val bytes = zip.readNBytesCompat(maxBlobBytes + 1)
                        if (bytes.size <= maxBlobBytes) blobs += attachment.path to bytes
                    }
                    name.endsWith(".md", ignoreCase = true) -> {
                        val bytes = zip.readNBytesCompat(maxFileBytes + 1)
                        if (bytes.size <= maxFileBytes) md += name to bytes.toString(Charsets.UTF_8)
                    }
                }
            }
        }
        return Content(stripCommonRoot(md), blobs)
    }

    /**
     * Все .md из архива: путь без общей верхней папки (архив мог быть упакован как «Pin/…» или просто «tasks/…»).
     * Слишком большие файлы и чужие расширения пропускаются.
     */
    fun read(input: InputStream, maxFileBytes: Int = 2 * 1024 * 1024): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory || !name.endsWith(".md", ignoreCase = true) || name.contains("../")) continue
                val bytes = zip.readNBytesCompat(maxFileBytes + 1)
                if (bytes.size > maxFileBytes) continue
                out += name to bytes.toString(Charsets.UTF_8)
            }
        }
        return stripCommonRoot(out)
    }

    private fun ZipInputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (buffer.size() < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (n <= 0) break
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    fun stripCommonRoot(files: List<Pair<String, String>>): List<Pair<String, String>> {
        val known = setOf("tasks", "people", "birthdays", "holidays", "plans", "gifts", "lists", "journal", ".pin")
        val firsts = files.map { it.first.substringBefore('/', "") }.toSet()
        val root = firsts.singleOrNull()?.takeIf { it.isNotEmpty() && it !in known } ?: return files
        return files.map { (path, text) -> path.removePrefix("$root/") to text }
    }
}
