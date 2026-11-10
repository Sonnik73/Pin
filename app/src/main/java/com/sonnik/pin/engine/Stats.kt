package com.sonnik.pin.engine

import java.time.LocalDate

/** Событие журнала для статистики (без Android и Room). */
data class StatEvent(
    val entryId: String,
    /** DONE, ROLLED, MISSED. */
    val event: String,
    val day: Long,
    val kind: EntryType,
    val priority: Int,
    val rollovers: Int,
    val onTime: Boolean,
)

/** Итог одного дня. Тот же набор цифр отдаётся Крепости (`day_stats`). */
data class DayStat(
    val day: Long,
    /** Сколько было на этот день: выполненное + невыполненное (долги) + ещё открытое сегодня. */
    val planned: Int,
    val done: Int,
    val doneOnTime: Int,
    /** Задачи, не выполненные в этот день и переехавшие на следующий. */
    val rolledOver: Int,
    /** Пропущенные повторы (день повтора — этот). */
    val missed: Int,
    /** Выполнено важных. */
    val importantDone: Int,
    /** Пропуски без ночей переноса — у них нет события ROLLED за этот день. */
    val missedSameDay: Int = 0,
) {
    /** Долги дня: что не сделано в свой день. */
    val debts: Int get() = rolledOver + missedSameDay
}

/** Сводка за период. */
data class StatsSummary(
    val days: List<DayStat>,
    val done: Int,
    val planned: Int,
    val onTime: Int,
    val rolledOver: Int,
    val missed: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val byKind: List<Pair<EntryType, Int>>,
    val byTag: List<Pair<String, Int>>,
) {
    val onTimeShare: Double? get() = if (done == 0) null else onTime.toDouble() / done
    val doneShare: Double? get() = if (planned == 0) null else done.toDouble() / planned
}

/** Группа дней на графике: день, неделя или месяц. */
data class StatBucket(val label: String, val from: Long, val to: Long, val done: Int, val debts: Int, val planned: Int)

object Stats {
    /** Не задачи: праздники — события, привычки считаются отдельно ([Habits]). */
    val NOT_TASKS = setOf(EntryType.HOLIDAY, EntryType.HABIT)

    /**
     * Цифры по дням в отрезке [from]..[to].
     * [openByDay] — открытые сейчас задачи по дням (для сегодняшнего дня: что ещё запланировано, но не сделано).
     */
    fun days(events: List<StatEvent>, from: Long, to: Long, openByDay: Map<Long, Int> = emptyMap()): List<DayStat> {
        val byDay = events.filter { it.day in from..to && it.kind !in NOT_TASKS }.groupBy { it.day }
        return (from..to).map { d ->
            val list = byDay[d].orEmpty()
            val done = list.filter { it.event == "DONE" }
            val rolled = list.count { it.event == "ROLLED" }
            val missed = list.filter { it.event == "MISSED" }
            val missedSameDay = missed.count { it.rollovers == 0 }
            DayStat(
                day = d,
                planned = done.size + rolled + missedSameDay + (openByDay[d] ?: 0),
                done = done.size,
                doneOnTime = done.count { it.onTime },
                rolledOver = rolled,
                missed = missed.size,
                importantDone = done.count { it.priority >= Priority.HIGH.level },
                missedSameDay = missedSameDay,
            )
        }
    }

    /**
     * Серии дней без долгов. Текущая — от вчера назад (сегодня ещё не закончился; если сегодня уже без долгов
     * и всё сделано, он тоже засчитывается). Дни до первого события журнала не считаются.
     */
    fun streaks(events: List<StatEvent>, today: Long): Pair<Int, Int> {
        val relevant = events.filter { it.kind !in NOT_TASKS }
        val first = relevant.minOfOrNull { it.day } ?: return 0 to 0
        val debtDays = relevant.filter { it.event == "ROLLED" || (it.event == "MISSED" && it.rollovers == 0) }.mapTo(HashSet()) { it.day }
        var best = 0
        var run = 0
        for (d in first until today) {
            if (d in debtDays) run = 0 else run++
            best = maxOf(best, run)
        }
        var current = 0
        var d = today - 1
        while (d >= first && d !in debtDays) {
            current++
            d--
        }
        return current to best
    }

    fun summary(
        events: List<StatEvent>,
        from: Long,
        to: Long,
        today: Long,
        tagsOf: (String) -> List<String>,
        openByDay: Map<Long, Int> = emptyMap(),
    ): StatsSummary {
        val days = days(events, from, to, openByDay)
        val inRange = events.filter { it.day in from..to && it.kind !in NOT_TASKS }
        val done = inRange.filter { it.event == "DONE" }
        val (current, best) = streaks(events, today)
        return StatsSummary(
            days = days,
            done = done.size,
            planned = days.sumOf { it.planned },
            onTime = done.count { it.onTime },
            rolledOver = days.sumOf { it.rolledOver },
            missed = days.sumOf { it.missed },
            currentStreak = current,
            bestStreak = best,
            byKind = done.groupingBy { it.kind }.eachCount().toList().sortedByDescending { it.second },
            byTag = done.flatMap { e -> tagsOf(e.entryId).ifEmpty { listOf("") }.map { it } }
                .groupingBy { it }.eachCount().toList()
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }),
        )
    }

    /** Неделя по дням, месяц по дням, год по месяцам. */
    fun buckets(days: List<DayStat>, byMonth: Boolean): List<StatBucket> {
        if (!byMonth) {
            return days.map { d ->
                val date = LocalDate.ofEpochDay(d.day)
                StatBucket(date.dayOfMonth.toString(), d.day, d.day, d.done, d.debts, d.planned)
            }
        }
        return days.groupBy { LocalDate.ofEpochDay(it.day).withDayOfMonth(1) }.toSortedMap().map { (month, list) ->
            StatBucket(MONTH_SHORT[month.monthValue - 1], list.first().day, list.last().day, list.sumOf { it.done }, list.sumOf { it.debts }, list.sumOf { it.planned })
        }
    }

    private val MONTH_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    fun kindTitle(kind: EntryType): String = when (kind) {
        EntryType.ONE_TIME -> "Разовые"
        EntryType.RECURRING -> "Периодические"
        EntryType.BIRTHDAY -> "Дни рождения"
        EntryType.HOLIDAY -> "Праздники"
        EntryType.PLAN -> "Планы"
        EntryType.GIFT -> "Подарки"
        EntryType.HABIT -> "Привычки"
        EntryType.MOMENT -> "Важные даты"
    }
}
