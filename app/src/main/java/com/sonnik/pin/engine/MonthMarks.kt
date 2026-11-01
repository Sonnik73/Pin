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
