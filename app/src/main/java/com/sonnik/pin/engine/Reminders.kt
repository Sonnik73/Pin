package com.sonnik.pin.engine

import java.time.LocalDate
import java.time.ZoneId

/** Напоминание по времени задачи (или привычки — тогда [taskId] это id привычки). */
data class Reminder(val taskId: String, val at: Long, val title: String)

/** Привычка с временем напоминания. */
data class HabitAlarm(val habit: HabitInfo, val title: String, val timeMinutes: Int)

/**
 * Расписание уведомлений — чистые функции: что показать сейчас и когда проснуться в следующий раз.
 * Будильник ставится один — на ближайшее событие; при срабатывании показывается всё, что наступило.
 */
object Reminders {
    /**
     * Момент напоминания задачи: время на календарную дату её дня. Если время раньше границы дня
     * (задача на 01:00 при границе 04:00), это уже следующая календарная дата — та же логическая ночь.
     */
    fun reminderAt(task: TaskSnapshot, zone: ZoneId, boundaryMinutes: Int): Long? {
        val minutes = task.timeMinutes
        val base = minutes?.let { dayAt(task.currentDay, it, zone, boundaryMinutes) - task.remindBefore * 60_000L }
        // Отложенное напоминание: не раньше назначенного момента (после переноса на другой день снова работает время задачи).
        val snooze = task.snoozeUntil
        return when {
            base == null -> snooze
            snooze == null -> base
            else -> maxOf(base, snooze)
        }
    }

    /** Момент времени [minutes] логического дня [day] (время раньше границы дня — уже следующая дата). */
    fun dayAt(day: Long, minutes: Int, zone: ZoneId, boundaryMinutes: Int): Long {
        var date = LocalDate.ofEpochDay(day)
        if (minutes < boundaryMinutes) date = date.plusDays(1)
        return date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
    }

    /**
     * Напоминания привычек: в дни по расписанию, если за этот день ещё нет отметки.
     * Смотрим вчера, сегодня и завтра — этого хватает для будильника на ближайшее событие.
     */
    fun habitMoments(alarm: HabitAlarm, today: Long, zone: ZoneId, boundaryMinutes: Int): List<Reminder> =
        (today - 1..today + 1)
            .filter { Habits.isScheduled(alarm.habit, it) && it !in alarm.habit.doneDays }
            .map { Reminder(alarm.habit.id, dayAt(it, alarm.timeMinutes, zone, boundaryMinutes), alarm.title) }

    fun habitsDue(alarms: List<HabitAlarm>, today: Long, after: Long, now: Long, zone: ZoneId, boundaryMinutes: Int): List<Reminder> =
        alarms.flatMap { habitMoments(it, today, zone, boundaryMinutes) }.filter { it.at > after && it.at <= now }.sortedBy { it.at }

    fun nextHabit(alarms: List<HabitAlarm>, today: Long, now: Long, zone: ZoneId, boundaryMinutes: Int): Long? =
        alarms.flatMap { habitMoments(it, today, zone, boundaryMinutes) }.map { it.at }.filter { it > now }.minOrNull()

    /** Варианты «напомнить заранее», минут. */
    val ADVANCE_OPTIONS = listOf(0, 5, 15, 30, 60)

    /** «за 15 минут», «за час», «в срок». */
    fun describeAdvance(minutes: Int): String = when (minutes) {
        0 -> "в срок"
        60 -> "за час"
        else -> "за $minutes ${RuText.plural(minutes, "минуту", "минуты", "минут")}"
    }

    /** Напоминания, наступившие в полуинтервале ([after], [now]]: ровно один раз каждое. */
    fun due(tasks: List<TaskSnapshot>, after: Long, now: Long, zone: ZoneId, boundaryMinutes: Int): List<Reminder> =
        tasks.asSequence()
            .filter { it.status == TaskStatus.OPEN }
            .mapNotNull { t -> reminderAt(t, zone, boundaryMinutes)?.let { Reminder(t.id, it, t.title) } }
            .filter { it.at > after && it.at <= now }
            .sortedBy { it.at }
            .toList()

    /** Ближайшее напоминание после [now]. */
    fun nextReminder(tasks: List<TaskSnapshot>, now: Long, zone: ZoneId, boundaryMinutes: Int): Long? =
        tasks.asSequence()
            .filter { it.status == TaskStatus.OPEN }
            .mapNotNull { reminderAt(it, zone, boundaryMinutes) }
            .filter { it > now }
            .minOrNull()

    /** Момент утренней сводки для логического дня [day]. */
    fun morningAt(day: Long, morningMinutes: Int, zone: ZoneId, boundaryMinutes: Int): Long {
        var date = LocalDate.ofEpochDay(day)
        if (morningMinutes < boundaryMinutes) date = date.plusDays(1)
        return date.atStartOfDay(zone).plusMinutes(morningMinutes.toLong()).toInstant().toEpochMilli()
    }

    /**
     * Ближайшая утренняя сводка после [now], ещё не показанная: [lastMorningDay] — последний день, за который она была.
     */
    fun nextMorning(today: Long, lastMorningDay: Long, now: Long, morningMinutes: Int, zone: ZoneId, boundaryMinutes: Int): Long {
        val todayAt = morningAt(today, morningMinutes, zone, boundaryMinutes)
        return if (lastMorningDay < today && todayAt > now) todayAt else morningAt(today + 1, morningMinutes, zone, boundaryMinutes)
    }

    /** Пора ли показать утреннюю сводку за [today]. */
    fun morningDue(today: Long, lastMorningDay: Long, now: Long, morningMinutes: Int, zone: ZoneId, boundaryMinutes: Int): Boolean =
        lastMorningDay < today && now >= morningAt(today, morningMinutes, zone, boundaryMinutes)
}

/** Текст утренней сводки. null — показывать нечего. */
data class MorningSummary(val title: String, val text: String) {
    companion object {
        fun of(board: TodayBoard, events: List<String> = emptyList()): MorningSummary? {
            val open = board.rolledOver + board.today
            if (open.isEmpty() && events.isEmpty()) return null
            val n = open.size
            val title = if (n == 0) "Сегодня задач нет" else "Сегодня $n ${RuText.plural(n, "задача", "задачи", "задач")}"
            val parts = ArrayList<String>()
            if (events.isNotEmpty()) parts += events.joinToString(" · ")
            if (board.rolledOver.isNotEmpty()) parts += "перенесено: ${board.rolledOver.size}"
            val lines = open.sortedWith(compareByDescending<TaskSnapshot> { it.priority.level }.thenBy { it.timeMinutes ?: Int.MAX_VALUE })
                .take(5)
                .map { t -> (t.timeMinutes?.let { RuText.time(it) + " " } ?: "") + t.title }
            val more = if (n > 5) "\n…и ещё ${n - 5}" else ""
            val header = parts.joinToString(", ").replaceFirstChar { it.uppercase() }
            val text = listOf(header, lines.joinToString("\n") + more).filter { it.isNotBlank() }.joinToString("\n")
            return MorningSummary(title, text)
        }
    }
}
