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

    /**
     * [ongoing] — период «по настоящее время»: даты окончания ещё нет, дни прибавляются каждый день.
     */
    fun counter(start: Long, end: Long?, today: Long, ongoing: Boolean = false): Counter {
        val period = end?.takeIf { it > start && !ongoing }
        val total = today - start
        val main = when {
            total < 0 -> "Через " + span(today, start)
            total == 0L -> "Сегодня"
            // Когда счёт идёт на месяцы и годы, полезно видеть и сами дни.
            total >= 32 -> "Прошло " + span(start, today) + " (" + days(total) + ")"
            else -> "Прошло " + span(start, today)
        }
        if (ongoing) {
            val detail = if (today < start) "без даты окончания" else "идёт: день ${today - start + 1} · по настоящее время"
            return Counter(main, detail)
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
    /** Запись для итога: день, последний день (null — одна дата), идёт ли «по настоящее время». */
    data class Item(val start: Long, val end: Long? = null, val ongoing: Boolean = false)

    fun summary(items: List<Item>, today: Long): Summary {
        val year = LocalDate.ofEpochDay(today).year
        val yearStart = LocalDate.of(year, 1, 1).toEpochDay()
        val yearEnd = LocalDate.of(year, 12, 31).toEpochDay()
        // Идущий период считается по сегодняшний день (ещё не начавшийся — пока ноль дней).
        val periods = items.mapNotNull { i ->
            when {
                i.ongoing -> i.start to maxOf(i.start - 1, today)
                else -> i.end?.takeIf { it > i.start }?.let { i.start to it }
            }
        }
        return Summary(
            count = items.size,
            dates = items.size - periods.size,
            periods = periods.size,
            periodDays = periods.sumOf { (s, e) -> maxOf(0L, length(s, e)) },
            daysThisYear = periods.sumOf { (s, e) -> maxOf(0L, minOf(e, yearEnd) - maxOf(s, yearStart) + 1) },
            year = year,
        )
    }

    /** «10 ноября 2026» или для периода «10–26 ноября 2026 · 17 дней», «28 декабря 2026 – 8 января 2027 · 12 дней». */
    fun describe(start: Long, end: Long?, ongoing: Boolean = false, today: Long? = null): String {
        val s = LocalDate.ofEpochDay(start)
        val full = { d: LocalDate -> RuText.dayMonth(d.dayOfMonth, d.monthValue) + " " + d.year }
        // «с 10 ноября 2026 по настоящее время · 17 дней».
        if (ongoing) return "с " + full(s) + " по настоящее время" + if (today != null && today >= start) " · " + days(length(start, today)) else ""
        val last = end?.takeIf { it > start } ?: return full(s)
        val e = LocalDate.ofEpochDay(last)
        val range = when {
            s.year == e.year && s.monthValue == e.monthValue -> "${s.dayOfMonth}–" + full(e)
            s.year == e.year -> RuText.dayMonth(s.dayOfMonth, s.monthValue) + " – " + full(e)
            else -> full(s) + " – " + full(e)
        }
        return range + " · " + days(length(start, last))
    }

    // ---------- Статистика ----------

    /** Важная дата для статистики. */
    data class Entry(
        val id: String,
        val title: String,
        val start: Long,
        val end: Long? = null,
        val ongoing: Boolean = false,
        val group: String? = null,
        val remind: Remind? = null,
    ) {
        val item: Item get() = Item(start, end, ongoing)

        /** Идёт ли сегодня: начался и не закончился (или «по настоящее время»). */
        fun isActive(today: Long): Boolean =
            start <= today && (ongoing || (end != null && end > start && today <= end))
    }

    /** Год: сколько записей началось и сколько дней периодов пришлось на него. */
    data class YearRow(val year: Int, val count: Int, val periodDays: Long)

    data class Stats(
        val total: Summary,
        /** Идущие сейчас периоды. */
        val active: List<Entry>,
        /** Последнее прошедшее (или сегодняшнее) событие и ближайшее будущее. */
        val last: Entry?,
        val next: Entry?,
        /** Группы по числу записей. */
        val groups: List<Pair<String, Summary>>,
        /** По годам, новые сверху. */
        val years: List<YearRow>,
    )

    fun stats(entries: List<Entry>, today: Long): Stats {
        val byGroup = entries.filter { groupName(it.group) != null }.groupBy { groupName(it.group)!!.lowercase() }
        val groups = byGroup.values
            .sortedWith(compareByDescending<List<Entry>> { it.size }.thenBy { groupName(it.first().group)!!.lowercase() })
            .map { list -> groupName(list.first().group)!! to summary(list.map { it.item }, today) }
        val years = entries.flatMap { e ->
            val from = LocalDate.ofEpochDay(e.start).year
            val last = when {
                e.ongoing -> maxOf(e.start, today)
                e.end != null && e.end > e.start -> e.end
                else -> e.start
            }
            (from..LocalDate.ofEpochDay(last).year).map { it to e }
        }.groupBy({ it.first }, { it.second }).map { (year, list) ->
            val ys = LocalDate.of(year, 1, 1).toEpochDay()
            val ye = LocalDate.of(year, 12, 31).toEpochDay()
            val days = list.sumOf { e ->
                val last = when {
                    e.ongoing -> today
                    e.end != null && e.end > e.start -> e.end
                    else -> return@sumOf 0L
                }
                maxOf(0L, minOf(last, ye) - maxOf(e.start, ys) + 1)
            }
            YearRow(year, list.count { LocalDate.ofEpochDay(it.start).year == year }, days)
        }.filter { it.count > 0 || it.periodDays > 0 }.sortedByDescending { it.year }
        return Stats(
            total = summary(entries.map { it.item }, today),
            active = entries.filter { it.isActive(today) }.sortedBy { it.start },
            last = entries.filter { it.start <= today }.maxWithOrNull(compareBy<Entry> { it.start }.thenBy { it.id }),
            next = entries.filter { it.start > today }.minWithOrNull(compareBy<Entry> { it.start }.thenBy { it.id }),
            groups = groups,
            years = years,
        )
    }

    // ---------- «Напоминать каждый…» ----------

    enum class Every { WEEK, MONTH, YEAR }

    /** Напоминание о важной дате: каждую неделю в [weekday] (1 — понедельник … 7 — воскресенье), каждый месяц или год. */
    data class Remind(val every: Every, val weekday: Int = 1) {
        val text: String get() = format(this)
    }

    private val WEEKDAYS_DATIVE = listOf("понедельникам", "вторникам", "средам", "четвергам", "пятницам", "субботам", "воскресеньям")
    val WEEKDAYS_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    /** «year», «month», «week:2»; что-то непонятное — без напоминаний. */
    fun parseRemind(text: String?): Remind? {
        val t = text?.trim()?.lowercase() ?: return null
        return when {
            t == "year" -> Remind(Every.YEAR)
            t == "month" -> Remind(Every.MONTH)
            t.startsWith("week") -> Remind(Every.WEEK, t.substringAfter(':', "1").toIntOrNull()?.takeIf { it in 1..7 } ?: 1)
            else -> null
        }
    }

    fun format(r: Remind): String = when (r.every) {
        Every.YEAR -> "year"
        Every.MONTH -> "month"
        Every.WEEK -> "week:${r.weekday.coerceIn(1, 7)}"
    }

    /** «Каждый год — 10 ноября», «Каждый месяц — 10-го числа», «Каждую неделю — по вторникам». */
    fun describeRemind(r: Remind, start: Long): String {
        val s = LocalDate.ofEpochDay(start)
        return when (r.every) {
            Every.YEAR -> "Каждый год — " + RuText.dayMonth(s.dayOfMonth, s.monthValue)
            Every.MONTH -> "Каждый месяц — ${s.dayOfMonth}-го числа" + if (s.dayOfMonth > 28) " (в коротких месяцах — в последний день)" else ""
            Every.WEEK -> "Каждую неделю — по " + WEEKDAYS_DATIVE[r.weekday.coerceIn(1, 7) - 1]
        }
    }

    /**
     * Напомнить ли о дате в день [day]. Только после самого события: в его день счётчик и так «Сегодня».
     * 31-е в коротком месяце и 29 февраля в обычный год — в последний день месяца.
     */
    fun remindsOn(start: Long, r: Remind, day: Long): Boolean {
        if (day <= start) return false
        val s = LocalDate.ofEpochDay(start)
        val d = LocalDate.ofEpochDay(day)
        return when (r.every) {
            Every.WEEK -> d.dayOfWeek.value == r.weekday
            Every.MONTH -> d.dayOfMonth == minOf(s.dayOfMonth, d.lengthOfMonth())
            Every.YEAR -> d.monthValue == s.monthValue && d.dayOfMonth == minOf(s.dayOfMonth, d.lengthOfMonth())
        }
    }

    /** Уведомление о дате: «📍 Переехали» / «Прошло 1 год (365 дней) · 10 ноября 2025». */
    data class Notice(val id: String, val title: String, val text: String)

    fun notices(entries: List<Entry>, today: Long): List<Notice> = entries
        .filter { e -> e.remind?.let { remindsOn(e.start, it, today) } == true }
        .sortedBy { it.start }
        .map { e ->
            val c = counter(e.start, e.end, today, e.ongoing)
            Notice(e.id, "📍 " + e.title, listOfNotNull(c.main, c.detail, describe(e.start, e.end, e.ongoing)).joinToString(" · "))
        }

    // ---------- Календарь и «Сегодня» ----------

    /** Дни в [from]..[to], когда дата с напоминанием отмечается в календаре: сам день события и дни напоминаний. */
    fun calendarDays(e: Entry, from: Long, to: Long): List<Long> {
        val r = e.remind ?: return emptyList()
        val first = if (e.start in from..to) listOf(e.start) else emptyList()
        return first + (maxOf(from, e.start + 1)..to).filter { remindsOn(e.start, r, it) }
    }

    /** Подпись в календаре: «1 год с этого дня» или «важная дата» в сам день. */
    fun calendarNote(e: Entry, day: Long): String = if (day <= e.start) "важная дата" else span(e.start, day) + " с этого дня"

    /** За сколько дней показывать в «Сегодня»: годовщину — за неделю, ежемесячное — накануне, еженедельное — в сам день. */
    private fun aheadDays(r: Remind): Int = when (r.every) {
        Every.YEAR -> 7
        Every.MONTH -> 1
        Every.WEEK -> 0
    }

    /** Ближайшие напоминания о датах для «Сегодня», как дни рождения: (день, дата). */
    fun upcoming(entries: List<Entry>, today: Long): List<Pair<Long, Entry>> = entries
        .flatMap { e ->
            val r = e.remind ?: return@flatMap emptyList()
            (today..today + aheadDays(r)).filter { remindsOn(e.start, r, it) }.map { it to e }
        }
        .sortedWith(compareBy<Pair<Long, Entry>> { it.first }.thenBy { it.second.start })

    /** «📍 Сегодня — Переехали: 1 год», «📍 Через 5 дней — Свадьба: 6 лет». */
    fun upcomingText(day: Long, e: Entry, today: Long): String {
        val whenText = when (val left = day - today) {
            0L -> "Сегодня"
            1L -> "Завтра"
            2L -> "Послезавтра"
            else -> "Через " + days(left)
        }
        return "📍 $whenText — ${e.title}: ${span(e.start, day)}"
    }

    /** События календаря (как дни рождения): «📍 10 — Переехали · 1 год с этого дня». */
    fun calendarEvents(entries: List<Entry>, from: Long, to: Long): List<CalendarEvent> =
        entries.flatMap { e -> calendarDays(e, from, to).map { d -> CalendarEvent(d, false, e.title + " · " + calendarNote(e, d), emoji = "📍") } }
}
