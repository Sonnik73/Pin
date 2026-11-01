package com.sonnik.pin.engine

import java.time.LocalDate

/** Привычка для расчётов: расписание (null — каждый день) и дни, когда отмечена. */
data class HabitInfo(
    val id: String,
    val rule: RepeatRule? = null,
    /** С какого дня привычка ведётся (дни раньше не считаются пропущенными). */
    val startDay: Long,
    val doneDays: Set<Long>,
)

data class HabitStats(
    /** Текущая серия: подряд выполненные дни по расписанию (сегодня ещё не отмечена — серия не прервана). */
    val current: Int,
    val best: Int,
    /** Выполнено из запланированных за последние 30 дней, 0..1; null — запланированных не было. */
    val rate30: Double?,
    val doneToday: Boolean,
    val scheduledToday: Boolean,
)

/** Клетка тепловой карты. */
data class HeatCell(val day: Long, val scheduled: Boolean, val done: Boolean, val future: Boolean)

object Habits {
    /**
     * Неделя привычки для кольца: сколько отмечено с понедельника по [today] и сколько дней по плану на всю неделю.
     * Дни до начала привычки не считаются.
     */
    fun week(habit: HabitInfo, today: Long): Pair<Int, Int> {
        val monday = today - (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        val days = monday..monday + 6
        val planned = days.count { isScheduled(habit, it) }
        val done = days.count { it <= today && it >= habit.startDay && it in habit.doneDays }
        return done to planned
    }

    fun isScheduled(habit: HabitInfo, day: Long): Boolean {
        if (day < habit.startDay) return false
        val rule = habit.rule ?: return true
        return rule.nextAfter(day - 1, habit.startDay) == day
    }

    fun stats(habit: HabitInfo, today: Long): HabitStats {
        val done = habit.doneDays
        // Текущая: от сегодня (если отмечена) или от вчера назад, по запланированным дням.
        var current = 0
        var d = if (today in done) today else today - 1
        while (d >= habit.startDay) {
            if (isScheduled(habit, d) || d in done) {
                if (d in done) current++ else break
            }
            d--
        }
        var best = 0
        var run = 0
        for (day in habit.startDay..today) {
            val scheduled = isScheduled(habit, day)
            when {
                day in done -> { run++; best = maxOf(best, run) }
                scheduled && day < today -> run = 0
            }
        }
        val from = maxOf(habit.startDay, today - 29)
        val planned = (from..today).filter { isScheduled(habit, it) && (it < today || it in done) }
        val rate = if (planned.isEmpty()) null else planned.count { it in done }.toDouble() / planned.size
        return HabitStats(current, best, rate, today in done, isScheduled(habit, today))
    }

    /** Тепловая карта: недели (столбцы, с понедельника) за [weeks] недель до текущей включительно. */
    fun heatmap(habit: HabitInfo, today: Long, weeks: Int = 53): List<List<HeatCell>> {
        val monday = LocalDate.ofEpochDay(today).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toEpochDay()
        val first = monday - (weeks - 1) * 7L
        return (0 until weeks).map { w ->
            (0 until 7).map { i ->
                val day = first + w * 7 + i
                HeatCell(day, isScheduled(habit, day), day in habit.doneDays, day > today)
            }
        }
    }

    /** Сколько привычек отмечено и сколько запланировано в день — для «Итогов» и Крепости. */
    fun dayCounts(habits: List<HabitInfo>, day: Long): Pair<Int, Int> =
        habits.count { day in it.doneDays } to habits.count { isScheduled(it, day) }
}
