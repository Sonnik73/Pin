package com.sonnik.pin.engine

import java.time.LocalDate
import java.time.ZoneId

/**
 * Напоминание по времени задачи (или привычки — тогда [taskId] это id привычки).
 * [before] — за сколько минут до времени задачи (null — отложенное из уведомления или привычка).
 */
data class Reminder(val taskId: String, val at: Long, val title: String, val before: Int? = null)

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
    fun reminderAt(task: TaskSnapshot, zone: ZoneId, boundaryMinutes: Int): Long? =
        moments(task, zone, boundaryMinutes).minOfOrNull { it.first }

    /**
     * Все моменты напоминаний задачи: по одному на каждое «напомнить за…» (момент и за сколько минут).
     * «Отложить» из уведомления: напоминания раньше назначенного момента молчат, вместо них — одно в этот момент.
     * Если все напоминания позже (задачу перенесли на другой день), отложенное уже ни при чём.
     */
    fun moments(task: TaskSnapshot, zone: ZoneId, boundaryMinutes: Int): List<Pair<Long, Int?>> {
        val base: List<Pair<Long, Int?>> = task.timeMinutes?.let { minutes ->
            val at = dayAt(task.currentDay, minutes, zone, boundaryMinutes)
            task.reminders.distinct().sortedDescending().map { before -> at - before * 60_000L to before }
        } ?: emptyList()
        val snooze = task.snoozeUntil ?: return base
        val later = base.filter { it.first >= snooze }
        return if (later.size == base.size && base.isNotEmpty()) base else listOf(snooze to null) + later
    }

    /** Сколько напоминаний можно поставить одной задаче. */
    const val MAX_REMINDERS = 5

    /** Напоминания из поля записи: «0,120,1440»; null — запись из старой версии, тогда одно — [legacy]. */
    fun parse(text: String?, legacy: Int): List<Int> = when (text) {
        null -> listOf(legacy.coerceIn(0, MAX_ADVANCE))
        else -> normalize(text.split(',').mapNotNull { it.trim().toIntOrNull() })
    }

    fun format(list: List<Int>): String = normalize(list).joinToString(",")

    /** Без повторов, в пределах недели, не больше [MAX_REMINDERS], по возрастанию. */
    fun normalize(list: List<Int>): List<Int> = list.map { it.coerceIn(0, MAX_ADVANCE) }.distinct().sorted().take(MAX_REMINDERS)

    /** Какое «напомнить за…» предложить в новой строке: первое из привычных, которого ещё нет. */
    fun suggestNext(current: List<Int>): Int =
        listOf(60, 120, 24 * 60, 30, 15, 0, 3 * 60, 2 * 24 * 60).firstOrNull { it !in current } ?: 60

    /** Текст уведомления: «В 18:00 — через 2 часа» или «Пора: на 18:00». */
    fun noticeText(before: Int?, timeMinutes: Int?): String? {
        if (timeMinutes == null || before == null) return null
        val time = RuText.time(timeMinutes)
        return if (before <= 0) "Пора: на $time" else "В $time — через " + describeAdvance(before).removePrefix("за ")
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

    /** Своё «напомнить заранее» — до недели. */
    const val MAX_ADVANCE = 7 * 24 * 60

    /** «в срок», «за 15 минут», «за час», «за 2 ч 30 мин», «за день», «за 3 дня». */
    fun describeAdvance(minutes: Int): String {
        val days = minutes / (24 * 60)
        val hours = minutes % (24 * 60) / 60
        val mins = minutes % 60
        return when {
            minutes <= 0 -> "в срок"
            minutes == 60 -> "за час"
            minutes < 60 -> "за $minutes ${RuText.plural(minutes, "минуту", "минуты", "минут")}"
            days == 0 && mins == 0 -> "за $hours ${RuText.plural(hours, "час", "часа", "часов")}"
            days == 0 -> "за $hours ч $mins мин"
            hours == 0 && mins == 0 -> if (days == 1) "за день" else "за $days ${RuText.plural(days, "день", "дня", "дней")}"
            else -> "за " + listOfNotNull("$days дн", hours.takeIf { it > 0 }?.let { "$it ч" }, mins.takeIf { it > 0 }?.let { "$it мин" }).joinToString(" ")
        }
    }

    /** Напоминания, наступившие в полуинтервале ([after], [now]]: ровно один раз каждое. */
    fun due(tasks: List<TaskSnapshot>, after: Long, now: Long, zone: ZoneId, boundaryMinutes: Int): List<Reminder> =
        tasks.asSequence()
            .filter { it.status == TaskStatus.OPEN }
            .flatMap { t -> moments(t, zone, boundaryMinutes).map { (at, before) -> Reminder(t.id, at, t.title, before) } }
            .filter { it.at > after && it.at <= now }
            .sortedBy { it.at }
            .toList()

    /** Ближайшее напоминание после [now]. */
    fun nextReminder(tasks: List<TaskSnapshot>, now: Long, zone: ZoneId, boundaryMinutes: Int): Long? =
        tasks.asSequence()
            .filter { it.status == TaskStatus.OPEN }
            .flatMap { t -> moments(t, zone, boundaryMinutes).map { it.first } }
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

/**
 * Постоянное уведомление — коротко, в одну-две узкие строки: сколько дел на сегодня и первые из них.
 * «3 на сегодня · 1 перенесено» / «18:00 Созвон · Хлеб · Отчёт +2».
 */
data class PinnedNotice(val title: String, val text: String) {
    companion object {
        private const val SHOWN = 3
        private const val TITLE_MAX = 28

        /** [doneToday] — сколько уже выполнено сегодня. */
        fun of(board: TodayBoard, doneToday: Int = board.done.size): PinnedNotice {
            val open = board.rolledOver + board.today
            val n = open.size
            if (n == 0) {
                return if (doneToday > 0) PinnedNotice("✓ Всё на сегодня сделано", "Выполнено: $doneToday")
                else PinnedNotice("На сегодня задач нет", "Нажмите, чтобы открыть Pin")
            }
            val title = "$n на сегодня" + if (board.rolledOver.isNotEmpty()) " · ${board.rolledOver.size} ${RuText.plural(board.rolledOver.size, "перенесена", "перенесены", "перенесено")}" else ""
            val first = open.sortedWith(compareByDescending<TaskSnapshot> { it.priority.level }.thenBy { it.timeMinutes ?: Int.MAX_VALUE })
                .take(SHOWN)
                .map { t ->
                    val name = if (t.title.length > TITLE_MAX) t.title.take(TITLE_MAX - 1).trimEnd() + "…" else t.title
                    (t.timeMinutes?.let { RuText.time(it) + " " } ?: "") + name
                }
            val more = if (n > SHOWN) " +${n - SHOWN}" else ""
            return PinnedNotice(title, first.joinToString(" · ") + more)
        }
    }
}
