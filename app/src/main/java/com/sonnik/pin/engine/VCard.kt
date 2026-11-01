package com.sonnik.pin.engine

/** Человек для обмена: имя, кто это, заметка и день рождения (год — по желанию). */
data class SharedPerson(
    val name: String,
    val relation: String = "",
    val note: String = "",
    val month: Int? = null,
    val day: Int? = null,
    val year: Int? = null,
    val remindDays: List<Int> = emptyList(),
) {
    val hasBirthday: Boolean get() = month != null && day != null
}

/**
 * Обмен людьми через vCard (.vcf): такой файл понимают Pin, «Контакты» и почти любые приложения.
 * Пишем vCard 3.0 с BDAY и своими полями X-PIN-*; читаем терпимо: свёрнутые строки, разные форматы BDAY,
 * файлы из Google Контактов и телефонных книг.
 */
object VCard {
    fun write(people: List<SharedPerson>): String = buildString {
        for (p in people) {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:").append(escape(p.name)).append("\r\n")
            append("N:").append(escape(p.name)).append(";;;;\r\n")
            if (p.hasBirthday) {
                val md = "%02d-%02d".format(p.month, p.day)
                append("BDAY:").append(if (p.year != null) "%04d-%s".format(p.year, md) else "--$md").append("\r\n")
            }
            if (p.note.isNotBlank()) append("NOTE:").append(escape(p.note)).append("\r\n")
            if (p.relation.isNotBlank()) append("X-PIN-RELATION:").append(escape(p.relation)).append("\r\n")
            if (p.remindDays.isNotEmpty()) append("X-PIN-REMIND:").append(p.remindDays.joinToString(",")).append("\r\n")
            append("END:VCARD\r\n")
        }
    }

    fun parse(text: String): List<SharedPerson> {
        // Развернуть свёрнутые строки (продолжение начинается с пробела или таба).
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
            .replace(Regex("\n[ \t]"), "")
            .lines()
        val result = ArrayList<SharedPerson>()
        var fields: MutableMap<String, String>? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> fields = HashMap()
                line.equals("END:VCARD", ignoreCase = true) -> {
                    fields?.let { toPerson(it) }?.let(result::add)
                    fields = null
                }
                fields != null -> {
                    val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                    // «item1.BDAY;VALUE=date» → BDAY
                    val key = line.substring(0, colon).substringBefore(';').substringAfterLast('.').uppercase()
                    fields.putIfAbsent(key, line.substring(colon + 1))
                }
            }
        }
        return result
    }

    private fun toPerson(f: Map<String, String>): SharedPerson? {
        val name = f["FN"]?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }
            ?: f["N"]?.split(';')?.let { parts -> listOf(parts.getOrNull(1), parts.getOrNull(0)) }
                ?.mapNotNull { it?.let(::unescape)?.trim()?.takeIf(String::isNotEmpty) }?.joinToString(" ")?.takeIf { it.isNotEmpty() }
            ?: return null
        val bday = f["BDAY"]?.let(::parseBirthday)
        return SharedPerson(
            name = name,
            relation = f["X-PIN-RELATION"]?.let(::unescape).orEmpty(),
            note = f["NOTE"]?.let(::unescape).orEmpty(),
            month = bday?.first,
            day = bday?.second,
            year = bday?.third,
            remindDays = f["X-PIN-REMIND"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 0..60 }.orEmpty(),
        )
    }

    /** «1997-11-10», «19971110», «--11-10», «--1110», «1997-11-10T00:00:00Z» → (месяц, день, год?). */
    fun parseBirthday(value: String): Triple<Int, Int, Int?>? {
        val v = value.trim().substringBefore('T')
        val noYear = v.startsWith("--")
        val digits = v.removePrefix("--").filter(Char::isDigit)
        val (year, rest) = when {
            noYear && digits.length == 4 -> null to digits
            !noYear && digits.length == 8 -> digits.take(4).toInt() to digits.drop(4)
            else -> return null
        }
        val month = rest.take(2).toInt()
        val day = rest.drop(2).toInt()
        if (month !in 1..12 || runCatching { java.time.MonthDay.of(month, day) }.isFailure) return null
        // 1604 — так Apple помечает «год не указан».
        return Triple(month, day, year?.takeIf { it in 1801..2200 })
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun unescape(s: String) = s.replace(Regex("""\\([nN,;\\])""")) { m ->
        when (val c = m.groupValues[1]) {
            "n", "N" -> "\n"
            else -> c
        }
    }
}

/** Что сделает импорт с человеком из файла. */
enum class ImportAction(val title: String) {
    ADD("новый"),
    ADD_BIRTHDAY("добавится день рождения"),
    SKIP("уже есть"),
}

object PeopleImport {
    /**
     * План импорта: по имени (без регистра, ё = е). Нового — добавить; если человек есть без дня рождения,
     * а в файле он есть — дописать дату; иначе пропустить (свои данные не перезаписываем).
     */
    fun plan(existing: Map<String, Boolean>, incoming: List<SharedPerson>): List<Pair<SharedPerson, ImportAction>> {
        val known = existing.mapKeys { Search.normalize(it.key) }
        val seen = HashSet<String>()
        return incoming.map { p ->
            val key = Search.normalize(p.name)
            val action = when {
                !seen.add(key) -> ImportAction.SKIP
                key !in known -> ImportAction.ADD
                known[key] == false && p.hasBirthday -> ImportAction.ADD_BIRTHDAY
                else -> ImportAction.SKIP
            }
            p to action
        }
    }
}
