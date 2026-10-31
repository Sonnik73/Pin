package com.sonnik.pin.engine

import java.time.DayOfWeek
import java.time.LocalDate

/** Русские подписи дат и чисел — чистые функции, чтобы проверять их тестами. */
object RuText {
    private val MONTHS_GENITIVE = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    private val WEEKDAYS_SHORT = mapOf(
        DayOfWeek.MONDAY to "пн", DayOfWeek.TUESDAY to "вт", DayOfWeek.WEDNESDAY to "ср",
        DayOfWeek.THURSDAY to "чт", DayOfWeek.FRIDAY to "пт", DayOfWeek.SATURDAY to "сб", DayOfWeek.SUNDAY to "вс",
    )

    /** Форма слова для числа: plural(5, "раз", "раза", "раз"). */
    fun plural(n: Long, one: String, few: String, many: String): String {
        val abs = kotlin.math.abs(n)
        val mod100 = abs % 100
        val mod10 = abs % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1L -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    fun plural(n: Int, one: String, few: String, many: String): String = plural(n.toLong(), one, few, many)

    /** «перенесено 3 раза». */
    fun rollovers(n: Int): String = "перенесено $n ${plural(n, "раз", "раза", "раз")}"

    /** «7 ноября», с годом, если он не совпадает с годом [today]. */
    fun date(day: Long, today: Long): String {
        val d = LocalDate.ofEpochDay(day)
        val base = "${d.dayOfMonth} ${MONTHS_GENITIVE[d.monthValue - 1]}"
        return if (d.year == LocalDate.ofEpochDay(today).year) base else "$base ${d.year}"
    }

    /** «сегодня», «завтра», «вчера», «пт, 7 ноября». */
    fun relativeDay(day: Long, today: Long): String = when (day - today) {
        0L -> "сегодня"
        1L -> "завтра"
        2L -> "послезавтра"
        -1L -> "вчера"
        else -> "${WEEKDAYS_SHORT.getValue(LocalDate.ofEpochDay(day).dayOfWeek)}, ${date(day, today)}"
    }

    /** «09:05». */
    fun time(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** «Воскресенье, 1 ноября» — заголовок экрана «Сегодня». */
    fun headline(day: Long): String {
        val d = LocalDate.ofEpochDay(day)
        val weekday = when (d.dayOfWeek) {
            DayOfWeek.MONDAY -> "Понедельник"
            DayOfWeek.TUESDAY -> "Вторник"
            DayOfWeek.WEDNESDAY -> "Среда"
            DayOfWeek.THURSDAY -> "Четверг"
            DayOfWeek.FRIDAY -> "Пятница"
            DayOfWeek.SATURDAY -> "Суббота"
            else -> "Воскресенье"
        }
        return "$weekday, ${d.dayOfMonth} ${MONTHS_GENITIVE[d.monthValue - 1]}"
    }
}
