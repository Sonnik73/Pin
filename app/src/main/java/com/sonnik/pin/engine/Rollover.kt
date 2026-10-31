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

/**
 * Правило повтора задачи и день его первого повтора.
 * [rollsOver] = false — повтор не переносится: не выполненный в свой день сразу закрывается как пропущенный
 * (так ведут себя дни рождения и праздники — «Поздравить» не должно висеть год).
 */
data class Schedule(val rule: RepeatRule, val anchor: Long, val rollsOver: Boolean = true)

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
    /** Типы с расписанием: переносятся по правилу повтора. */
    val SCHEDULED_TYPES = setOf(EntryType.RECURRING, EntryType.BIRTHDAY, EntryType.HOLIDAY)

    fun needsRollover(task: TaskSnapshot, today: Long, schedule: Schedule? = null): Boolean =
        task.status == TaskStatus.OPEN && task.currentDay < today &&
            (task.type == EntryType.ONE_TIME || (task.type in SCHEDULED_TYPES && schedule != null))

    /** Переносит одну задачу; null — переносить нечего. */
    fun apply(task: TaskSnapshot, today: Long, schedule: Schedule? = null): RolloverResult? {
        if (!needsRollover(task, today, schedule)) return null
        if (task.type in SCHEDULED_TYPES && schedule != null) {
            return if (schedule.rollsOver) applyRecurring(task, today, schedule) else applyExpiring(task, today, schedule)
        }
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

    /** Повтор без переноса: каждый прошедший невыполненный повтор — пропущен, задача встаёт на ближайший с сегодняшнего. */
    private fun applyExpiring(task: TaskSnapshot, today: Long, schedule: Schedule): RolloverResult {
        val missed = ArrayList<MissedEvent>()
        var occurrence = task.originalDay
        while (occurrence < today) {
            missed += MissedEvent(task.id, occurrence, closedOn = maxOf(occurrence, task.currentDay) + 1, rollovers = task.rollovers)
            occurrence = schedule.rule.nextAfter(occurrence, schedule.anchor)
        }
        return RolloverResult(
            task = task.copy(originalDay = occurrence, currentDay = occurrence, rollovers = 0),
            events = emptyList(),
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
