package com.sonnik.pin.engine

/** Одна ночь переноса: задача [taskId] не выполнена в день [fromDay] и переехала на [fromDay] + 1. */
data class RolloverEvent(val taskId: String, val fromDay: Long) {
    val toDay: Long get() = fromDay + 1
}

/** Итог переноса одной задачи. */
data class RolloverResult(
    val task: TaskSnapshot,
    /** По одному событию на каждую пропущенную границу дня — для журнала и статистики. */
    val events: List<RolloverEvent>,
)

/**
 * Перенос невыполненных задач на следующий день.
 *
 * Детерминирован: результат зависит только от задач и сегодняшнего дня. Если приложение не открывали
 * несколько дней, перенос досчитывается за каждую пропущенную ночь — задача со вчера переедет на сегодня
 * со счётчиком +1, задача с позавчера — со счётчиком +2. Исходный день ([TaskSnapshot.originalDay]) сохраняется.
 * Повторный вызов с тем же днём ничего не меняет.
 */
object Rollover {
    /** Типы, которые переносятся так. Периодические задачи (0.2) переносятся до следующего повтора. */
    private val ROLLING_TYPES = setOf(EntryType.ONE_TIME, EntryType.BIRTHDAY, EntryType.HOLIDAY, EntryType.GIFT)

    fun needsRollover(task: TaskSnapshot, today: Long): Boolean =
        task.status == TaskStatus.OPEN && task.type in ROLLING_TYPES && task.currentDay < today

    /** Переносит одну задачу; null — переносить нечего. */
    fun apply(task: TaskSnapshot, today: Long): RolloverResult? {
        if (!needsRollover(task, today)) return null
        val nights = today - task.currentDay
        val events = (task.currentDay until today).map { RolloverEvent(task.id, it) }
        return RolloverResult(
            task = task.copy(currentDay = today, rollovers = task.rollovers + nights.toInt()),
            events = events,
        )
    }

    /** Переносит все задачи, которым это нужно, в порядке исходного списка. */
    fun applyAll(tasks: List<TaskSnapshot>, today: Long): List<RolloverResult> = tasks.mapNotNull { apply(it, today) }
}
