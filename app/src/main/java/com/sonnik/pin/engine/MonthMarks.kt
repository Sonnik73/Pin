package com.sonnik.pin.engine

/** Что стоит в календаре на день: открытая задача, будущий повтор или выполненное. */
enum class MarkKind { OPEN, PROJECTED, DONE }

data class MarkItem(val day: Long, val kind: MarkKind, val type: EntryType, val important: Boolean = false)

/** Значки под числом: точки (не больше трёх), «!» — важная, 🎂 — день рождения, красное число — праздник. */
data class DayMarks(val dots: List<MarkKind>, val important: Boolean, val birthday: Boolean, val holiday: Boolean)

/** Те же правила, что у клеток вкладки «Календарь», — для виджета календаря. */
object MonthMarks {
    fun build(items: List<MarkItem>): Map<Long, DayMarks> = items.groupBy { it.day }.mapValues { (_, list) ->
        val tasks = list.filter { it.type != EntryType.HABIT }
        val important = tasks.any {
            it.kind != MarkKind.DONE && it.important && it.type != EntryType.HOLIDAY && it.type != EntryType.BIRTHDAY
        }
        val dots = tasks
            .filter { it.type != EntryType.BIRTHDAY && it.type != EntryType.HOLIDAY }
            .sortedBy { it.kind.ordinal }
            .take(if (important) 2 else 3)
            .map { it.kind }
        DayMarks(
            dots = dots,
            important = important,
            birthday = list.any { it.type == EntryType.BIRTHDAY },
            holiday = list.any { it.type == EntryType.HOLIDAY && it.kind != MarkKind.DONE },
        )
    }
}

/** Подпись дня рождения или праздника под виджетом календаря. */
data class CalendarEvent(val day: Long, val birthday: Boolean, val name: String, val age: Int? = null) {
    /** «🎂 14 — Аня, 51» или «🎉 4 — День народного единства». */
    fun label(): String {
        val dom = java.time.LocalDate.ofEpochDay(day).dayOfMonth
        return if (birthday) "🎂 $dom — $name" + (age?.let { ", $it" } ?: "") else "🎉 $dom — $name"
    }

    companion object {
        /** Ближайшие сначала; в текущем месяце прошедшие дни не показываем. */
        fun upcoming(events: List<CalendarEvent>, fromDay: Long): List<CalendarEvent> =
            events.filter { it.day >= fromDay }.distinctBy { Triple(it.day, it.birthday, it.name) }.sortedWith(compareBy({ it.day }, { !it.birthday }, { it.name }))
    }
}
