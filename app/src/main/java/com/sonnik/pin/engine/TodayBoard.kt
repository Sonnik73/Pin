package com.sonnik.pin.engine

/** Содержимое экрана «Сегодня», разложенное по разделам. */
data class TodayBoard(
    /** Перенесённые с прошлых дней — сверху, самые «зависшие» первыми. */
    val rolledOver: List<TaskSnapshot>,
    /** Задачи на сегодня: важные, потом по времени. */
    val today: List<TaskSnapshot>,
    /** Выполненные сегодня. */
    val done: List<TaskSnapshot>,
    /** Ближайшие дни после сегодняшнего. */
    val upcoming: List<TaskSnapshot>,
) {
    val openCount: Int get() = rolledOver.size + today.size

    /** Прогресс дня для шапки: сделано из всего, что на сегодня (с перенесёнными). */
    val progress: DayProgress get() = DayProgress(
        done = done.size,
        total = done.size + openCount,
        rolled = rolledOver.size,
        important = (rolledOver + today).count { it.priority == Priority.HIGH },
    )

    companion object {
        /** Сколько дней вперёд показывать в «Скоро». */
        const val UPCOMING_DAYS = 7

        /**
         * Раскладывает задачи. Ожидается, что перенос уже выполнен; если нет — просроченные открытые
         * задачи всё равно попадут в «Перенесённые».
         *
         * @param doneDayOf логический день выполнения задачи — по нему выполненные попадают в «Сегодня».
         */
        fun build(
            tasks: List<TaskSnapshot>,
            today: Long,
            doneDayOf: (TaskSnapshot) -> Long?,
            upcomingDays: Int = UPCOMING_DAYS,
        ): TodayBoard {
            // Праздники — события дня, а не задачи; их показывают отдельно.
            val visible = tasks.filter { it.type != EntryType.HOLIDAY }
            val open = visible.filter { it.status == TaskStatus.OPEN }
            val rolled = open
                .filter { it.currentDay <= today && (it.rollovers > 0 || it.currentDay < today) }
                .sortedWith(compareByDescending<TaskSnapshot> { it.rollovers + (today - it.currentDay) }.then(dayOrder))
            val rolledIds = rolled.mapTo(HashSet()) { it.id }
            val todays = open.filter { it.currentDay == today && it.id !in rolledIds }.sortedWith(dayOrder)
            val done = visible.filter { it.status == TaskStatus.DONE && doneDayOf(it) == today }.sortedWith(dayOrder)
            val upcoming = open
                .filter { it.currentDay > today && it.currentDay <= today + upcomingDays }
                .sortedWith(compareBy<TaskSnapshot> { it.currentDay }.then(dayOrder))
            return TodayBoard(rolled, todays, done, upcoming)
        }

        /** Порядок внутри дня: важные выше, затем по времени (без времени — в конце), затем по созданию. */
        val dayOrder: Comparator<TaskSnapshot> =
            compareByDescending<TaskSnapshot> { it.priority.level }
                .thenBy { it.timeMinutes ?: Int.MAX_VALUE }
                .thenBy { it.createdAt }
                .thenBy { it.id }
    }
}

/** Выполнение задачи: вовремя — если отмечена не позже дня, на который её ставили изначально. */
object Completion {
    fun isOnTime(task: TaskSnapshot, completedDay: Long): Boolean = completedDay <= task.originalDay
}

/** Прогресс дня: «3 из 7», доля для кольца и подпись. */
data class DayProgress(val done: Int, val total: Int, val rolled: Int = 0, val important: Int = 0) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val allDone: Boolean get() = total > 0 && done == total

    /** «Сделано 3 из 7», «Всё сделано!», «Пока ничего не сделано из 5». */
    val title: String get() = when {
        total == 0 -> "На сегодня задач нет"
        allDone -> "Всё сделано!"
        done == 0 -> "Сделано 0 из $total"
        else -> "Сделано $done из $total"
    }

    /** «2 перенесено · 1 важная» — что ещё ждёт. */
    val subtitle: String get() = buildList {
        val left = total - done
        if (left > 0) add("осталось $left")
        if (rolled > 0) add("перенесено $rolled")
        if (important > 0) add(RuText.plural(important, "важная", "важные", "важных").let { "$important $it" })
    }.joinToString(" · ")
}
