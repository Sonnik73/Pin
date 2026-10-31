package com.sonnik.pin.engine

/** Одна ночь переноса: задача [taskId] не выполнена в день [fromDay] и переехала на [fromDay] + 1. */
data class RolloverEvent(val taskId: String, val fromDay: Long) {
    val toDay: Long get() = fromDay + 1
}

/**
 * Повтор периодической задачи на [occurrenceDay] так и не выполнен: в день [closedOn] наступил следующий повтор,
 * и этот закрыт как пропущенный. [rollovers] — сколько ночей он успел провисеть.
 */
data class MissedEvent(val taskId: String, val occurrenceDay: Long, val closedOn: Long, val rollovers: Int)

/** Итог переноса одной задачи. */
data class RolloverResult(
    val task: TaskSnapshot,
    /** По одному событию на каждую пропущенную границу дня — для журнала и статистики. */
    val events: List<RolloverEvent>,
    /** Пропущенные повторы периодической задачи. */
    val missed: List<MissedEvent> = emptyList(),
)

/** Правило повтора задачи и день его первого повтора. */
data class Schedule(val rule: RepeatRule, val anchor: Long)

/**
 * Перенос невыполненных задач на следующий день.
 *
 * Детерминирован: результат зависит только от задач и сегодняшнего дня. Если приложение не открывали
 * несколько дней, перенос досчитывается за каждую пропущенную ночь — задача со вчера переедет на сегодня
 * со счётчиком +1, задача с позавчера — со счётчиком +2. Исходный день ([TaskSnapshot.originalDay]) сохраняется.
 * Повторный вызов с тем же днём ничего не меняет.
 *
 * Периодическая задача хранит текущий повтор: [TaskSnapshot.originalDay] — его день, [TaskSnapshot.currentDay] —
 * куда он перенесён. Невыполненный повтор переносится, пока не наступит следующий; тогда старый закрывается
 * как пропущенный, счётчик переносов обнуляется, и задача встаёт на новый повтор — дубли не копятся.
 */
object Rollover {
    /** Типы, которые переносятся до выполнения. */
    private val ROLLING_TYPES = setOf(EntryType.ONE_TIME, EntryType.BIRTHDAY, EntryType.HOLIDAY, EntryType.GIFT)

    fun needsRollover(task: TaskSnapshot, today: Long, schedule: Schedule? = null): Boolean =
        task.status == TaskStatus.OPEN && task.currentDay < today &&
            (task.type in ROLLING_TYPES || (task.type == EntryType.RECURRING && schedule != null))

    /** Переносит одну задачу; null — переносить нечего. */
    fun apply(task: TaskSnapshot, today: Long, schedule: Schedule? = null): RolloverResult? {
        if (!needsRollover(task, today, schedule)) return null
        if (task.type == EntryType.RECURRING && schedule != null) return applyRecurring(task, today, schedule)
        val nights = today - task.currentDay
        val events = (task.currentDay until today).map { RolloverEvent(task.id, it) }
        return RolloverResult(
            task = task.copy(currentDay = today, rollovers = task.rollovers + nights.toInt()),
            events = events,
        )
    }

    private fun applyRecurring(task: TaskSnapshot, today: Long, schedule: Schedule): RolloverResult {
        var occurrence = task.originalDay
        var current = task.currentDay
        var rollovers = task.rollovers
        val events = ArrayList<RolloverEvent>()
        val missed = ArrayList<MissedEvent>()
        while (current < today) {
            val next = schedule.rule.nextAfter(occurrence, schedule.anchor)
            if (next <= today) {
                // Повтор висит до дня перед следующим, а в ночь на следующий закрывается как пропущенный.
                for (d in current until next - 1) {
                    events += RolloverEvent(task.id, d)
                    rollovers++
                }
                missed += MissedEvent(task.id, occurrence, closedOn = next, rollovers = rollovers)
                occurrence = next
                current = next
                rollovers = 0
            } else {
                for (d in current until today) events += RolloverEvent(task.id, d)
                rollovers += (today - current).toInt()
                current = today
            }
        }
        return RolloverResult(
            task = task.copy(originalDay = occurrence, currentDay = current, rollovers = rollovers),
            events = events,
            missed = missed,
        )
    }

    /** Переносит все задачи, которым это нужно, в порядке исходного списка. */
    fun applyAll(tasks: List<TaskSnapshot>, today: Long, scheduleOf: (TaskSnapshot) -> Schedule? = { null }): List<RolloverResult> =
        tasks.mapNotNull { apply(it, today, scheduleOf(it)) }

    /**
     * Выполнение повтора периодической задачи: следующий повтор — первый после дня повтора и после дня выполнения
     * (выполнил понедельничный во вторник — следующий в следующий понедельник; выполнил пятничный заранее в среду —
     * следующий через неделю). Возвращает задачу, вставшую на следующий повтор.
     */
    fun nextOccurrence(task: TaskSnapshot, completedDay: Long, schedule: Schedule): TaskSnapshot {
        val next = schedule.rule.nextAfter(maxOf(task.originalDay, completedDay), schedule.anchor)
        return task.copy(originalDay = next, currentDay = next, rollovers = 0, status = TaskStatus.OPEN)
    }
}
