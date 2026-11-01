package com.sonnik.pin.engine

/**
 * Дни рождения из простого текста: на каждой строке имя и дата, в любом порядке.
 * «Аня 10.11.1997», «Мама — 04.03», «10/11/1997 Серёга», «Вася: 5 марта 1990», «Петя 1.2.85».
 * Строки без даты, пустые и начинающиеся с «#» пропускаются (их номера — в [Result.skipped]).
 */
object BirthdayText {
    data class Result(val people: List<SharedPerson>, val skipped: List<Int>)

    private val NUMERIC = Regex("""(?<![\d])(\d{1,2})[./\-](\d{1,2})(?:[./\-](\d{2}|\d{4}))?(?![\d])""")
    private val MONTHS = listOf(
        "январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
    )
    private val WORDY = Regex("""(?<![\d])(\d{1,2})\s+([а-яё]+)\.?(?:\s+(\d{4}))?(?:\s*г(?:ода|\.)?)?""", RegexOption.IGNORE_CASE)

    /** [currentYear] — чтобы понять двузначный год: «97» → 1997, «05» → 2005. */
    fun parse(text: String, currentYear: Int): Result {
        val people = ArrayList<SharedPerson>()
        val skipped = ArrayList<Int>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val found = findDate(line, currentYear)
            if (found == null) {
                skipped += index + 1
                return@forEachIndexed
            }
            val (date, range) = found
            val name = (line.substring(0, range.first) + " " + line.substring(range.last + 1))
                .replace(Regex("""[—–\-:;,|\t]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (name.isEmpty()) {
                skipped += index + 1
                return@forEachIndexed
            }
            people += SharedPerson(name = name, month = date.second, day = date.first, year = date.third)
        }
        return Result(people, skipped)
    }

    /** Дата в строке: (день, месяц, год?) и где она стоит. */
    private fun findDate(line: String, currentYear: Int): Pair<Triple<Int, Int, Int?>, IntRange>? {
        NUMERIC.find(line)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { fullYear(it, currentYear) }
            if (valid(day, month)) return Triple(day, month, year) to m.range
        }
        WORDY.find(line)?.let { m ->
            val word = m.groupValues[2].lowercase()
            val month = MONTHS.indexOfFirst { stem ->
                if (stem == "ма") word == "мая" || word == "май" else word.startsWith(stem)
            } + 1
            val day = m.groupValues[1].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            if (month > 0 && valid(day, month)) return Triple(day, month, year) to m.range
        }
        return null
    }

    private fun fullYear(text: String, currentYear: Int): Int {
        val y = text.toInt()
        if (text.length == 4) return y
        val century = currentYear / 100 * 100
        return if (century + y > currentYear) century - 100 + y else century + y
    }

    private fun valid(day: Int, month: Int) = month in 1..12 && runCatching { java.time.MonthDay.of(month, day) }.isSuccess
}
