package com.sonnik.pin.engine

import java.time.DayOfWeek
import java.time.LocalDate

/** Задача, разобранная из одной строки. */
data class QuickDraft(
    val title: String,
    val day: Long,
    val timeMinutes: Int? = null,
    val priority: Priority = Priority.NORMAL,
    val tags: List<String> = emptyList(),
    /** Распознанные слова о дате и времени — чтобы показать, как поняли строку. */
    val recognized: List<String> = emptyList(),
)

/**
 * Быстрое добавление одной строкой: «завтра купить хлеб», «в пятницу в 18:30 позвонить маме !», «12 ноября отчёт #работа».
 * Понимает: сегодня/завтра/послезавтра, дни недели, «через N дней/неделю/месяц», «12 ноября [2027]», «12.11[.2027]»,
 * время «в 18», «в 18:30», «18:30», «!» — важная, «#тег». Остальное — название.
 */
object QuickAdd {
    private const val L = """(?<![\p{L}\d])"""
    private const val R = """(?![\p{L}\d])"""

    private val MONTHS = listOf(
        "январ|янв", "феврал|фев", "март|мар", "апрел|апр", "ма[йя]", "июн", "июл", "август|авг",
        "сентябр|сен|сент", "октябр|окт", "ноябр|ноя|нояб", "декабр|дек",
    )

    private val WEEKDAYS = listOf(
        DayOfWeek.MONDAY to "понедельник|пн",
        DayOfWeek.TUESDAY to "вторник|вт",
        DayOfWeek.WEDNESDAY to "сред[ау]|ср",
        DayOfWeek.THURSDAY to "четверг|чт",
        DayOfWeek.FRIDAY to "пятниц[ау]|пт",
        DayOfWeek.SATURDAY to "суббот[ау]|сб",
        DayOfWeek.SUNDAY to "воскресенье|вс",
    )

    /** Необязательный предлог перед датой: «на завтра», «к 12 ноября», «в пятницу». */
    private const val PREP = """(?:(?:на|к|ко|в|во|до)\s+)?"""

    private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    fun parse(input: String, today: Long): QuickDraft {
        var text = " " + input.trim() + " "
        val recognized = ArrayList<String>()
        var day: Long? = null
        var time: Int? = null
        var priority = Priority.NORMAL
        val tags = ArrayList<String>()

        fun take(regex: Regex, handle: (MatchResult) -> Boolean) {
            val m = regex.find(text) ?: return
            if (handle(m)) {
                recognized += m.value.trim()
                text = text.removeRange(m.range).let { " $it " }
            }
        }

        // Теги и важность.
        while (true) {
            val m = Regex("""(?<=\s)#([\p{L}\d_-]+)""").find(text) ?: break
            tags += m.groupValues[1].lowercase()
            text = text.removeRange(m.range)
        }
        Regex("""(?<=\s)!{1,3}(?=\s)""").find(text)?.let {
            priority = Priority.HIGH
            text = text.removeRange(it.range)
        }

        val todayDate = LocalDate.ofEpochDay(today)

        // Дата с месяцем словом: «12 ноября», «1 янв 2027».
        val monthAlternatives = MONTHS.joinToString("|") { "(?:$it)[\\p{L}]*" }
        take(rx("""$L$PREP(\d{1,2})\s+($monthAlternatives)(?:\s+(\d{4}))?$R""")) { m ->
            val d = m.groupValues[1].toInt()
            val word = m.groupValues[2].lowercase()
            val month = MONTHS.indexOfFirst { Regex("^(?:$it)").containsMatchIn(word) } + 1
            val year = m.groupValues[3].toIntOrNull()
            resolveDate(d, month, year, todayDate)?.also { day = it } != null
        }
        // Дата цифрами: «12.11», «12.11.2027».
        if (day == null) {
            take(rx("""$L$PREP(\d{1,2})\.(\d{1,2})(?:\.(\d{4}))?$R""")) { m ->
                resolveDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull(), todayDate)?.also { day = it } != null
            }
        }
        if (day == null) {
            take(rx("""$L${PREP}послезавтра$R""")) { day = today + 2; true }
        }
        if (day == null) {
            take(rx("""$L${PREP}завтра$R""")) { day = today + 1; true }
        }
        if (day == null) {
            take(rx("""$L${PREP}сегодня$R""")) { day = today; true }
        }
        if (day == null) {
            take(rx("""${L}через\s+(\d{1,3})\s+(дн[яей]|день|недел[юиь]|месяц[аев]*)$R""")) { m ->
                val n = m.groupValues[1].toLong()
                val unit = m.groupValues[2].lowercase()
                day = when {
                    unit.startsWith("нед") -> today + 7 * n
                    unit.startsWith("мес") -> todayDate.plusMonths(n).toEpochDay()
                    else -> today + n
                }
                true
            }
        }
        if (day == null) {
            take(rx("""${L}через\s+(неделю|месяц)$R""")) { m ->
                day = if (m.groupValues[1].lowercase() == "неделю") today + 7 else todayDate.plusMonths(1).toEpochDay()
                true
            }
        }
        if (day == null) {
            for ((dow, words) in WEEKDAYS) {
                take(rx("""${L}(?:(?:в|во)\s+)?(?:$words)$R""")) { _ ->
                    val shift = (dow.value - todayDate.dayOfWeek.value + 7) % 7
                    day = today + shift
                    true
                }
                if (day != null) break
            }
        }

        // Время: «в 18:30», «в 9», «18:30».
        take(rx("""${L}в\s+(\d{1,2})(?::(\d{2}))?$R(?!\s*(?:$monthAlternatives|дн|день|недел))""")) { m ->
            parseTime(m.groupValues[1], m.groupValues[2])?.also { time = it } != null
        }
        if (time == null) {
            take(rx("""$L(\d{1,2}):(\d{2})$R""")) { m -> parseTime(m.groupValues[1], m.groupValues[2])?.also { time = it } != null }
        }

        val title = text.replace(Regex("""\s+"""), " ").trim().trim(',', '-', '—', ' ').replaceFirstChar { it.uppercase() }
        return QuickDraft(title, day ?: today, time, priority, tags, recognized)
    }

    private fun parseTime(h: String, m: String): Int? {
        val hours = h.toIntOrNull() ?: return null
        val minutes = if (m.isEmpty()) 0 else m.toIntOrNull() ?: return null
        return if (hours in 0..23 && minutes in 0..59) hours * 60 + minutes else null
    }

    /** Дата без года — ближайшая с сегодняшнего (прошедшая в этом году — значит в следующем). */
    private fun resolveDate(d: Int, month: Int, year: Int?, today: LocalDate): Long? {
        if (month !in 1..12) return null
        fun at(y: Int) = runCatching { LocalDate.of(y, month, d) }.getOrNull()
        if (year != null) return at(year)?.toEpochDay()
        val thisYear = at(today.year) ?: at(today.year + 1) ?: return null
        return (if (thisYear.isBefore(today)) at(today.year + 1) ?: at(today.year + 4) else thisYear)?.toEpochDay()
    }

    /** «завтра, 18:30, важная, #дом» — что поняли из строки. */
    fun describe(draft: QuickDraft, today: Long): String = buildList {
        add(RuText.relativeDay(draft.day, today))
        draft.timeMinutes?.let { add(RuText.time(it)) }
        if (draft.priority == Priority.HIGH) add("важная")
        draft.tags.forEach { add("#$it") }
    }.joinToString(", ")
}
