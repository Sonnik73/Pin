package com.sonnik.pin.engine

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Правило повтора периодической задачи. Строковая форма ([format]/[parse]) хранится в базе и в поле `repeat`
 * .md-файла, поэтому она человекочитаема и стабильна:
 *
 * - `daily`
 * - `every 3 days`
 * - `weekly mon,wed,fri`
 * - `monthly 15` или `monthly last`
 * - `yearly 03-08`
 *
 * Повторы отсчитываются от якоря — дня первого повтора (важно для «каждые N дней»).
 */
sealed interface RepeatRule {
    /** Первый повтор строго после [day] (эпохальные дни). [anchor] — день первого повтора. */
    fun nextAfter(day: Long, anchor: Long): Long

    data object Daily : RepeatRule {
        override fun nextAfter(day: Long, anchor: Long) = maxOf(day + 1, anchor)
    }

    data class EveryNDays(val n: Int) : RepeatRule {
        init {
            require(n >= 1) { "Интервал должен быть не меньше дня: $n" }
        }

        override fun nextAfter(day: Long, anchor: Long): Long {
            if (day < anchor) return anchor
            val steps = (day - anchor) / n + 1
            return anchor + steps * n
        }
    }

    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule {
        init {
            require(days.isNotEmpty()) { "Нужен хотя бы один день недели" }
        }

        override fun nextAfter(day: Long, anchor: Long): Long {
            var d = maxOf(day + 1, anchor)
            repeat(7) {
                if (LocalDate.ofEpochDay(d).dayOfWeek in days) return d
                d++
            }
            error("Недостижимо: в неделе есть нужный день")
        }
    }

    /** Ежемесячно в [dayOfMonth] (1–31; в коротких месяцах — последний день) или в последний день месяца. */
    data class Monthly(val dayOfMonth: Int?) : RepeatRule {
        init {
            require(dayOfMonth == null || dayOfMonth in 1..31) { "Число месяца 1–31: $dayOfMonth" }
        }

        val lastDay: Boolean get() = dayOfMonth == null

        private fun inMonth(year: Int, month: Int): LocalDate {
            val first = LocalDate.of(year, month, 1)
            val len = first.lengthOfMonth()
            return first.withDayOfMonth(if (dayOfMonth == null) len else minOf(dayOfMonth, len))
        }

        override fun nextAfter(day: Long, anchor: Long): Long {
            val from = maxOf(day + 1, anchor)
            val start = LocalDate.ofEpochDay(from)
            var month = start.withDayOfMonth(1)
            while (true) {
                val candidate = inMonth(month.year, month.monthValue).toEpochDay()
                if (candidate >= from) return candidate
                month = month.plusMonths(1)
            }
        }
    }

    /** Ежегодно [month]-[day]; 29 февраля в невисокосный год — 28 февраля. */
    data class Yearly(val month: Int, val day: Int) : RepeatRule {
        init {
            require(month in 1..12 && day in 1..31) { "Неверная дата: $month-$day" }
            require(day <= java.time.Month.of(month).maxLength()) { "Такой даты нет: $month-$day" }
        }

        fun inYear(year: Int): LocalDate {
            val first = LocalDate.of(year, month, 1)
            return first.withDayOfMonth(minOf(day, first.lengthOfMonth()))
        }

        override fun nextAfter(day: Long, anchor: Long): Long {
            val from = maxOf(day + 1, anchor)
            var year = LocalDate.ofEpochDay(from).year
            while (true) {
                val candidate = inYear(year).toEpochDay()
                if (candidate >= from) return candidate
                year++
            }
        }
    }

    companion object {
        private val DAY_CODES = listOf(
            DayOfWeek.MONDAY to "mon", DayOfWeek.TUESDAY to "tue", DayOfWeek.WEDNESDAY to "wed",
            DayOfWeek.THURSDAY to "thu", DayOfWeek.FRIDAY to "fri", DayOfWeek.SATURDAY to "sat", DayOfWeek.SUNDAY to "sun",
        )

        private val DAY_SHORT_RU = mapOf(
            DayOfWeek.MONDAY to "пн", DayOfWeek.TUESDAY to "вт", DayOfWeek.WEDNESDAY to "ср",
            DayOfWeek.THURSDAY to "чт", DayOfWeek.FRIDAY to "пт", DayOfWeek.SATURDAY to "сб", DayOfWeek.SUNDAY to "вс",
        )

        private val MONTHS_GENITIVE = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )

        fun format(rule: RepeatRule): String = when (rule) {
            Daily -> "daily"
            is EveryNDays -> if (rule.n == 1) "daily" else "every ${rule.n} days"
            is Weekly -> "weekly " + DAY_CODES.filter { it.first in rule.days }.joinToString(",") { it.second }
            is Monthly -> "monthly " + (rule.dayOfMonth?.toString() ?: "last")
            is Yearly -> "yearly %02d-%02d".format(rule.month, rule.day)
        }

        /** Разбор строки правила; null — правило не распознано (такую запись читаем как одноразовую). */
        fun parse(text: String?): RepeatRule? {
            val parts = text?.trim()?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return null
            if (parts.isEmpty()) return null
            return runCatching {
                when (parts[0]) {
                    "daily" -> Daily
                    "every" -> {
                        val n = parts.getOrNull(1)?.toIntOrNull() ?: return null
                        if (parts.getOrNull(2)?.startsWith("day") == false) return null
                        if (n == 1) Daily else EveryNDays(n)
                    }
                    "weekly" -> {
                        val codes = parts.drop(1).joinToString(",").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        val days = codes.mapNotNull { code -> DAY_CODES.firstOrNull { it.second == code.take(3) }?.first }.toSet()
                        if (days.isEmpty()) null else Weekly(days)
                    }
                    "monthly" -> when (val arg = parts.getOrNull(1)) {
                        "last" -> Monthly(null)
                        else -> Monthly(arg?.toIntOrNull() ?: return null)
                    }
                    "yearly" -> {
                        val (m, d) = parts.getOrNull(1)?.split('-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
                        Yearly(m, d)
                    }
                    else -> null
                }
            }.getOrNull()
        }

        /** «каждый день», «каждые 3 дня», «по пн, ср, пт», «каждое 15-е число», «ежегодно 8 марта». */
        fun describe(rule: RepeatRule): String = when (rule) {
            Daily -> "каждый день"
            is EveryNDays -> if (rule.n == 1) "каждый день" else "каждые ${rule.n} ${RuText.plural(rule.n, "день", "дня", "дней")}"
            is Weekly -> when (rule.days) {
                DayOfWeek.entries.toSet() -> "каждый день"
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "по будням"
                setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "по выходным"
                else -> "по " + DAY_CODES.filter { it.first in rule.days }.joinToString(", ") { DAY_SHORT_RU.getValue(it.first) }
            }
            is Monthly -> if (rule.lastDay) "в последний день месяца" else "каждое ${rule.dayOfMonth}-е число"
            is Yearly -> "ежегодно ${rule.day} ${MONTHS_GENITIVE[rule.month - 1]}"
        }

        /** Все повторы в отрезке [from]..[to] (включительно). */
        fun occurrences(rule: RepeatRule, anchor: Long, from: Long, to: Long, limit: Int = 400): List<Long> {
            val result = ArrayList<Long>()
            var d = rule.nextAfter(from - 1, anchor)
            while (d <= to && result.size < limit) {
                result += d
                d = rule.nextAfter(d, anchor)
            }
            return result
        }
    }
}
