package com.sonnik.pin.engine

/** Что стоит в календаре на день: открытая задача, будущий повтор или выполненное. */
enum class MarkKind { OPEN, PROJECTED, DONE }

data class MarkItem(
    val day: Long,
    val kind: MarkKind,
    val type: EntryType,
    val important: Boolean = false,
    /** Праздник-выходной — красное число; памятные и военные даты число не красят. */
    val dayOff: Boolean = true,
)

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
            holiday = list.any { it.type == EntryType.HOLIDAY && it.kind != MarkKind.DONE && it.dayOff },
        )
    }
}

/** Подпись дня рождения или праздника под виджетом календаря. */
data class CalendarEvent(val day: Long, val birthday: Boolean, val name: String, val age: Int? = null, val emoji: String = "🎉") {
    /** «🎂 14 — Аня, 51» или «🎉 4 — День народного единства». */
    fun label(): String {
        val dom = java.time.LocalDate.ofEpochDay(day).dayOfMonth
        return if (birthday) "🎂 $dom — $name" + (age?.let { ", $it" } ?: "") else "$emoji $dom — $name"
    }

    companion object {
        /** Ближайшие сначала; в текущем месяце прошедшие дни не показываем. */
        fun upcoming(events: List<CalendarEvent>, fromDay: Long): List<CalendarEvent> =
            events.filter { it.day >= fromDay }.distinctBy { Triple(it.day, it.birthday, it.name) }.sortedWith(compareBy({ it.day }, { !it.birthday }, { it.name }))
    }
}

/** Тепловая карта календаря: насколько подкрасить день по числу дел (0 — не красить, 4 — сильнее всего). */
object Heat {
    fun level(tasks: Int): Int = when {
        tasks <= 0 -> 0
        tasks == 1 -> 1
        tasks == 2 -> 2
        tasks <= 4 -> 3
        else -> 4
    }

    /** Прозрачность подкраски для уровня. */
    fun alpha(level: Int): Float = level.coerceIn(0, 4) * 0.07f

    /** Неделя дня [day] — с понедельника по воскресенье (эпохальные дни). */
    fun weekOf(day: Long): LongRange {
        val dow = java.time.LocalDate.ofEpochDay(day).dayOfWeek.value - 1
        return (day - dow)..(day - dow + 6)
    }
}
