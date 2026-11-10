package com.sonnik.pin.engine

import java.time.LocalDate
import java.time.Period

/**
 * Важные даты и периоды: «пришла награда», «отпуск с 10 ноября на 17 дней». Запись хранит день события
 * и, для периода, последний день; под ней — счётчик «прошло 1 год 2 месяца 3 дня».
 * Дни — эпохальные (календарные), без границы дня: событие помнят по дате.
 */
object Moments {
    /** Самый длинный период, дней (больше — скорее опечатка). */
    const val MAX_PERIOD_DAYS = 3660

    /** Счётчик под событием: главная строка и пояснение (для периода — идёт ли он и сколько осталось). */
    data class Counter(val main: String, val detail: String?)

    /** «1 год 2 месяца 3 дня» между двумя днями (порядок не важен); одинаковые дни — «0 дней». */
    fun span(from: Long, to: Long): String {
        val p = Period.between(LocalDate.ofEpochDay(minOf(from, to)), LocalDate.ofEpochDay(maxOf(from, to)))
        val parts = listOfNotNull(
            p.years.takeIf { it > 0 }?.let { "$it " + RuText.plural(it, "год", "года", "лет") },
            p.months.takeIf { it > 0 }?.let { "$it " + RuText.plural(it, "месяц", "месяца", "месяцев") },
            p.days.takeIf { it > 0 }?.let { days(it.toLong()) },
        )
        return parts.joinToString(" ").ifEmpty { days(0) }
    }

    /** «17 дней». */
    fun days(n: Long): String = "$n " + RuText.plural(n, "день", "дня", "дней")

    /** Длина периода в днях, включая первый и последний. */
    fun length(start: Long, end: Long): Long = end - start + 1

    /**
     * Период «с даты по дату»: начало и конец по порядку (выбрали конец раньше начала — меняются местами),
     * не длиннее [MAX_PERIOD_DAYS].
     */
    fun range(a: Long, b: Long): Pair<Long, Long> {
        val start = minOf(a, b)
        return start to minOf(maxOf(a, b), start + MAX_PERIOD_DAYS - 1)
    }

    /** Последний день периода, который начинается [start] и длится [days] дней. */
    fun endOf(start: Long, days: Int): Long = start + days.coerceIn(1, MAX_PERIOD_DAYS) - 1

    fun counter(start: Long, end: Long?, today: Long): Counter {
        val period = end?.takeIf { it > start }
        val total = today - start
        val main = when {
            total < 0 -> "Через " + span(today, start)
            total == 0L -> "Сегодня"
            // Когда счёт идёт на месяцы и годы, полезно видеть и сами дни.
            total >= 32 -> "Прошло " + span(start, today) + " (" + days(total) + ")"
            else -> "Прошло " + span(start, today)
        }
        val detail = period?.let { last ->
            val n = length(start, last)
            when {
                today < start -> "продлится " + days(n)
                today <= last -> {
                    val left = last - today
                    "идёт: день ${today - start + 1} из $n · " + if (left == 0L) "последний день" else "осталось " + days(left)
                }
                today == last + 1 -> "закончился вчера"
                else -> "закончился " + span(last, today) + " назад"
            }
        }
        return Counter(main, detail)
    }

    /** Название группы без лишних пробелов, с большой буквы; пустое — без группы. */
    fun groupName(text: String?): String? =
        text?.trim()?.replace(Regex("""\s+"""), " ")?.take(40)?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }

    /** Группы в порядке частоты, без учёта регистра (одна «Отпуска» на «отпуска» и «Отпуска»). */
    fun groups(names: List<String?>): List<String> =
        names.mapNotNull(::groupName).groupBy { it.lowercase() }.values
            .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.first().lowercase() })
            .map { it.first() }

    fun sameGroup(a: String?, b: String?): Boolean = groupName(a)?.lowercase() == groupName(b)?.lowercase()

    /** Итог по группе: сколько всего, сколько периодов и дней в них, сколько из этих дней — в году [today]. */
    data class Summary(val count: Int, val dates: Int, val periods: Int, val periodDays: Long, val daysThisYear: Long, val year: Int) {
        /** «5 записей · 3 периода — всего 45 дней · 2 события · в 2026 году — 24 дня». */
        val text: String
            get() = buildList {
                add("$count " + RuText.plural(count, "запись", "записи", "записей"))
                if (periods > 0) add("$periods " + RuText.plural(periods, "период", "периода", "периодов") + " — всего " + days(periodDays))
                if (periods > 0 && dates > 0) add("$dates " + RuText.plural(dates, "событие", "события", "событий"))
                if (periods > 0 && daysThisYear != periodDays) add("в $year году — " + days(daysThisYear))
            }.joinToString(" · ")
    }

    /** [items] — пары «день события, последний день периода (null — одна дата)». */
    fun summary(items: List<Pair<Long, Long?>>, today: Long): Summary {
        val year = LocalDate.ofEpochDay(today).year
        val yearStart = LocalDate.of(year, 1, 1).toEpochDay()
        val yearEnd = LocalDate.of(year, 12, 31).toEpochDay()
        val periods = items.mapNotNull { (s, e) -> e?.takeIf { it > s }?.let { s to it } }
        return Summary(
            count = items.size,
            dates = items.size - periods.size,
            periods = periods.size,
            periodDays = periods.sumOf { (s, e) -> length(s, e) },
            daysThisYear = periods.sumOf { (s, e) -> maxOf(0L, minOf(e, yearEnd) - maxOf(s, yearStart) + 1) },
            year = year,
        )
    }

    /** «10 ноября 2026» или для периода «10–26 ноября 2026 · 17 дней», «28 декабря 2026 – 8 января 2027 · 12 дней». */
    fun describe(start: Long, end: Long?): String {
        val s = LocalDate.ofEpochDay(start)
        val full = { d: LocalDate -> RuText.dayMonth(d.dayOfMonth, d.monthValue) + " " + d.year }
        val last = end?.takeIf { it > start } ?: return full(s)
        val e = LocalDate.ofEpochDay(last)
        val range = when {
            s.year == e.year && s.monthValue == e.monthValue -> "${s.dayOfMonth}–" + full(e)
            s.year == e.year -> RuText.dayMonth(s.dayOfMonth, s.monthValue) + " – " + full(e)
            else -> full(s) + " – " + full(e)
        }
        return range + " · " + days(length(start, last))
    }
}
