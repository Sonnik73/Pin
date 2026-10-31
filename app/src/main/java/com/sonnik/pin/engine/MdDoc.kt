package com.sonnik.pin.engine

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Файл записи: YAML-шапка и тело в markdown. Поддерживается то подмножество YAML, которое пишет Pin и которое
 * удобно править руками: `ключ: значение`, строки в кавычках, списки `[a, b]` и списки через `- ` на следующих строках.
 * Порядок полей сохраняется; неизвестные поля читаются и могут быть записаны обратно.
 */
data class MdDoc(val fields: LinkedHashMap<String, Any>, val body: String) {
    fun string(key: String): String? = when (val v = fields[key]) {
        null -> null
        is List<*> -> v.joinToString(", ")
        else -> v.toString().takeIf { it.isNotEmpty() }
    }

    fun list(key: String): List<String> = when (val v = fields[key]) {
        null -> emptyList()
        is List<*> -> v.map { it.toString() }.filter { it.isNotBlank() }
        else -> v.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun int(key: String): Int? = string(key)?.trim()?.toIntOrNull()
    fun long(key: String): Long? = string(key)?.trim()?.toLongOrNull()
    fun bool(key: String): Boolean? = when (string(key)?.trim()?.lowercase()) {
        "true", "yes", "да", "1" -> true
        "false", "no", "нет", "0" -> false
        else -> null
    }

    /** Дата `YYYY-MM-DD` → эпохальный день. */
    fun day(key: String): Long? = string(key)?.trim()?.let { runCatching { LocalDate.parse(it.take(10)).toEpochDay() }.getOrNull() }

    /** Дата-время ISO-8601 (со смещением или без) → epoch millis. */
    fun instant(key: String, zone: ZoneId): Long? = string(key)?.trim()?.let { MdDoc.parseInstant(it, zone) }

    fun render(): String = buildString {
        append("---\n")
        for ((k, v) in fields) {
            append(k).append(": ")
            when (v) {
                is List<*> -> append(v.joinToString(", ", "[", "]") { quoteIfNeeded(it.toString(), inList = true) })
                else -> append(quoteIfNeeded(v.toString(), inList = false))
            }
            append('\n')
        }
        append("---\n")
        if (body.isNotEmpty()) {
            append(body)
            if (!body.endsWith("\n")) append('\n')
        }
    }

    companion object {
        private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun formatInstant(millis: Long, zone: ZoneId): String =
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).withNano(0).format(ISO_OFFSET)

        fun formatDay(day: Long): String = LocalDate.ofEpochDay(day).toString()

        fun parseInstant(text: String, zone: ZoneId): Long? =
            runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { java.time.LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDate.parse(text.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()

        private val NEEDS_QUOTES = Regex("""^\s|\s$|^[\[{>|*&!%@`#'"-]|: | #|^(true|false|yes|no|null|~)$|^[\d.:+-]+$""", RegexOption.IGNORE_CASE)

        /** Числа, даты и время записываются как есть; строки, похожие на что-то другое, — в кавычках. */
        private fun quoteIfNeeded(s: String, inList: Boolean): String {
            if (s.isEmpty()) return "\"\""
            val plainNumberOrDate = Regex("""^-?\d+$|^\d{4}-\d{2}-\d{2}(T[\d:.+-]+Z?)?$""").matches(s)
            val needs = !plainNumberOrDate && (NEEDS_QUOTES.containsMatchIn(s) || s.contains('\n') || (inList && (s.contains(',') || s.contains(']'))))
            return if (needs) "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"" else s
        }

        /** Разбор файла. Файл без шапки — только тело (заголовок можно взять из первой строки `# …`). */
        fun parse(text: String): MdDoc {
            val normalized = text.replace("\r\n", "\n").removePrefix("﻿")
            val fields = LinkedHashMap<String, Any>()
            if (!normalized.startsWith("---\n")) return MdDoc(fields, normalized)
            val end = Regex("""\n---[ \t]*(\n|$)""").find(normalized, 3) ?: return MdDoc(fields, normalized)
            val header = normalized.substring(4, end.range.first + 1)
            val body = normalized.substring(end.range.last + 1)
            var lastKey: String? = null
            for (raw in header.split('\n')) {
                if (raw.isBlank() || raw.trimStart().startsWith("#")) continue
                val item = Regex("""^\s+-\s+(.*)$""").matchEntire(raw)
                if (item != null && lastKey != null) {
                    @Suppress("UNCHECKED_CAST")
                    val list = (fields[lastKey] as? MutableList<String>) ?: ArrayList<String>().also { fields[lastKey!!] = it }
                    list += unquote(item.groupValues[1].trim())
                    continue
                }
                val colon = raw.indexOf(':')
                if (colon <= 0) continue
                val key = raw.substring(0, colon).trim()
                val value = raw.substring(colon + 1).trim()
                lastKey = key
                fields[key] = when {
                    value.isEmpty() -> ArrayList<String>() // может продолжиться списком «- »; пустой список читается как пустое значение
                    value.startsWith("[") && value.endsWith("]") -> splitList(value.substring(1, value.length - 1))
                    else -> unquote(stripComment(value))
                }
            }
            // Пустые значения, за которыми не последовал список, — пустые строки.
            for ((k, v) in fields) if (v is List<*> && v.isEmpty()) fields[k] = ""
            return MdDoc(fields, body.removePrefix("\n"))
        }

        private fun stripComment(value: String): String {
            if (value.startsWith("\"") || value.startsWith("'")) return value
            val i = value.indexOf(" #")
            return if (i >= 0) value.substring(0, i).trimEnd() else value
        }

        private fun splitList(inner: String): MutableList<String> {
            val out = ArrayList<String>()
            val cur = StringBuilder()
            var quote: Char? = null
            var escape = false
            for (c in inner) {
                when {
                    escape -> { cur.append('\\').append(c); escape = false }
                    c == '\\' && quote == '"' -> escape = true
                    quote != null -> { cur.append(c); if (c == quote) quote = null }
                    c == '"' || c == '\'' -> { cur.append(c); quote = c }
                    c == ',' -> { out += unquote(cur.toString().trim()); cur.clear() }
                    else -> cur.append(c)
                }
            }
            if (cur.isNotBlank()) out += unquote(cur.toString().trim())
            return out.filter { it.isNotEmpty() }.toMutableList()
        }

        private fun unquote(v: String): String = when {
            v.length >= 2 && v.startsWith("\"") && v.endsWith("\"") ->
                v.substring(1, v.length - 1).replace(Regex("""\\(.)""")) { m ->
                    when (val c = m.groupValues[1]) {
                        "n" -> "\n"
                        "t" -> "\t"
                        else -> c
                    }
                }
            v.length >= 2 && v.startsWith("'") && v.endsWith("'") -> v.substring(1, v.length - 1).replace("''", "'")
            else -> v
        }

        /** Безопасное имя файла: без запрещённых в Windows/Android символов, не длиннее 80 знаков. */
        fun fileName(base: String): String {
            val cleaned = base.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("""\s+"""), " ").trim().trimEnd('.')
            return cleaned.take(80).trim().ifEmpty { "без названия" }
        }
    }
}

/** Журнал месяца — таблица markdown, по строке на событие. */
object JournalTable {
    val COLUMNS = listOf("day", "event", "id", "at", "kind", "priority", "rollovers", "on_time", "occurrence")

    data class Row(
        val day: Long,
        val event: String,
        val entryId: String,
        val at: Long,
        val kind: String,
        val priority: Int,
        val rollovers: Int,
        val onTime: Boolean,
        val occurrence: Long?,
    )

    fun render(month: String, rows: List<Row>, zone: ZoneId): String = buildString {
        append("---\ntype: journal\nmonth: ").append(month).append("\n---\n")
        append("| ").append(COLUMNS.joinToString(" | ")).append(" |\n")
        append("|").append(COLUMNS.joinToString("|") { "---" }).append("|\n")
        for (r in rows.sortedBy { it.at }) {
            val cells = listOf(
                MdDoc.formatDay(r.day), r.event, r.entryId, MdDoc.formatInstant(r.at, zone), r.kind.lowercase(),
                r.priority.toString(), r.rollovers.toString(), if (r.event == "DONE") r.onTime.toString() else "",
                r.occurrence?.let { MdDoc.formatDay(it) } ?: "",
            )
            append("| ").append(cells.joinToString(" | ")).append(" |\n")
        }
    }

    /** Строки таблицы; столбцы ищутся по заголовку, поэтому порядок и лишние столбцы не мешают. */
    fun parse(text: String, zone: ZoneId): List<Row> {
        val lines = MdDoc.parse(text).body.split('\n').map { it.trim() }.filter { it.startsWith("|") }
        if (lines.isEmpty()) return emptyList()
        fun cells(line: String) = line.trim('|').split('|').map { it.trim() }
        val header = cells(lines.first()).map { it.lowercase() }
        fun idx(name: String) = header.indexOf(name)
        return lines.drop(1).filterNot { it.replace(Regex("""[|\-:\s]"""), "").isEmpty() }.mapNotNull { line ->
            val c = cells(line)
            fun get(name: String) = idx(name).takeIf { it >= 0 }?.let { c.getOrNull(it) }.orEmpty()
            val day = runCatching { LocalDate.parse(get("day")).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            val id = get("id").ifEmpty { return@mapNotNull null }
            val event = get("event").uppercase().ifEmpty { return@mapNotNull null }
            Row(
                day = day,
                event = event,
                entryId = id,
                at = MdDoc.parseInstant(get("at"), zone) ?: LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli(),
                kind = get("kind").uppercase().ifEmpty { EntryType.ONE_TIME.name },
                priority = get("priority").toIntOrNull() ?: Priority.NORMAL.level,
                rollovers = get("rollovers").toIntOrNull() ?: 0,
                onTime = get("on_time").equals("true", ignoreCase = true),
                occurrence = runCatching { LocalDate.parse(get("occurrence")).toEpochDay() }.getOrNull(),
            )
        }
    }
}
