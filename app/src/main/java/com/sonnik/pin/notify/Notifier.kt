package com.sonnik.pin.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sonnik.pin.MainActivity
import com.sonnik.pin.R
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.MorningSummary
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.Reminders
import com.sonnik.pin.engine.TodayBoard

/**
 * Уведомления: утренняя сводка и напоминания по времени задач.
 * Будильник один — на ближайшее событие. При срабатывании [deliver] показывает всё наступившее
 * и ставит следующий. Расписание считает [Reminders] (чистые функции, покрыты тестами).
 */
/** Сколько минут осталось до времени дня [timeMinutes] (по местному времени; уже прошло — 0 или меньше). */
internal object RemindersTime {
    fun minutesUntil(timeMinutes: Int): Int {
        val local = java.time.LocalTime.now(java.time.ZoneId.systemDefault())
        return timeMinutes - (local.hour * 60 + local.minute)
    }
}

object Notifier {
    const val CHANNEL_MORNING = "morning"
    const val CHANNEL_REMINDERS = "reminders"
    private const val MORNING_ID = 1
    /** Напоминание не показывается, если пропущено больше чем на столько (телефон был выключен). */
    private const val STALE_MS = 6 * 60 * 60 * 1000L

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MORNING, "Утренняя сводка", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Что запланировано на день."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Задачи со временем."
            },
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Показать наступившее и поставить будильник на следующее событие. */
    suspend fun deliver(context: Context, repo: PinRepository) {
        repo.rollover()
        val now = repo.now()
        var settings = repo.settings()
        val day = repo.logicalDay()
        val today = day.today()
        val open = repo.openDated().mapNotNull { it.toSnapshot() }
        val habits = repo.habitAlarms()

        // Первый запуск или напоминания только что включили — прошлое не догоняем.
        val after = maxOf(if (settings.lastReminderAt == 0L) now else settings.lastReminderAt, now - STALE_MS)
        if (settings.remindersEnabled) {
            val due = Reminders.due(open, after, now, day.zone, day.boundaryMinutes)
            val byId = open.associateBy { it.id }
            if (canNotify(context)) due.forEach { r -> showReminder(context, r.taskId, r.title, r.at, byId[r.taskId]?.timeMinutes) }
            val habitDue = Reminders.habitsDue(habits, today, after, now, day.zone, day.boundaryMinutes)
            if (canNotify(context)) habitDue.forEach { r -> showHabit(context, r.taskId, r.title, day.dayOf(r.at)) }
        }
        val morningDue = settings.morningEnabled &&
            Reminders.morningDue(today, settings.lastMorningDay, now, settings.morningMinutes, day.zone, day.boundaryMinutes)
        if (morningDue && canNotify(context)) {
            val board = TodayBoard.build(open, today, doneDayOf = { null })
            val (holidays, notices) = repo.occasionNotices(today)
            MorningSummary.of(board, holidays + notices.map(Occasions::describe))?.let { showMorning(context, it, board.rolledOver.size) }
        }
        repo.updateSettingsQuietly {
            it.copy(lastReminderAt = now, lastMorningDay = if (morningDue) today else it.lastMorningDay)
        }
        settings = repo.settings()

        val next = listOfNotNull(
            if (settings.remindersEnabled) Reminders.nextReminder(open, now, day.zone, day.boundaryMinutes) else null,
            if (settings.remindersEnabled) Reminders.nextHabit(habits, today, now, day.zone, day.boundaryMinutes) else null,
            if (settings.morningEnabled) {
                Reminders.nextMorning(today, settings.lastMorningDay, now, settings.morningMinutes, day.zone, day.boundaryMinutes)
            } else {
                null
            },
            // На границе дня — перенос и пересчёт напоминаний перенесённых задач.
            day.nextBoundary(),
        ).minOrNull()
        schedule(context, next)
    }

    private fun schedule(context: Context, at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (at == null) return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notificationId(taskId: String): Int = 1000 + (taskId.hashCode() and 0x3fffffff)

    @SuppressLint("MissingPermission") // Проверено в canNotify.
    private fun showReminder(context: Context, taskId: String, title: String, at: Long, timeMinutes: Int?) {
        fun action(code: Int, action: String, extra: (Intent) -> Unit = {}): PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(taskId) * 4 + code,
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, taskId).also(extra),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = action(0, AlarmReceiver.ACTION_DONE)
        val snooze15 = action(1, AlarmReceiver.ACTION_SNOOZE) { it.putExtra(AlarmReceiver.EXTRA_MINUTES, 15) }
        val snooze60 = action(2, AlarmReceiver.ACTION_SNOOZE) { it.putExtra(AlarmReceiver.EXTRA_MINUTES, 60) }
        val tomorrow = action(3, AlarmReceiver.ACTION_TOMORROW)
        val text = when {
            timeMinutes == null -> "Напоминание"
            else -> {
                val left = RemindersTime.minutesUntil(timeMinutes)
                if (left in 1..180) "В ${com.sonnik.pin.engine.RuText.time(timeMinutes)} — через $left мин" else "Пора: на ${com.sonnik.pin.engine.RuText.time(timeMinutes)}"
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(at)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .addAction(0, "Готово", done)
            .addAction(0, "+15 мин", snooze15)
            .addAction(0, "+1 ч", snooze60)
            .addAction(0, "Завтра", tomorrow)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(taskId), notification) }
    }

    /** Напоминание привычки: «Отмечено» ставит отметку за этот день, не открывая приложение. */
    @SuppressLint("MissingPermission")
    private fun showHabit(context: Context, habitId: String, title: String, day: Long) {
        val mark = PendingIntent.getBroadcast(
            context,
            notificationId(habitId) * 4,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_HABIT)
                .putExtra(AlarmReceiver.EXTRA_ID, habitId)
                .putExtra(AlarmReceiver.EXTRA_DAY, day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔥 $title")
            .setContentText("Привычка на сегодня — не прервите серию")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .addAction(0, "Отмечено", mark)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(habitId), notification) }
    }

    @SuppressLint("MissingPermission")
    private fun showMorning(context: Context, summary: MorningSummary, rolledOver: Int) {
        val review = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(com.sonnik.pin.EXTRA_SCREEN, "review"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MORNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(summary.title)
            .setContentText(summary.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .apply { if (rolledOver > 0) addAction(0, "Разобрать перенесённые", review) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(MORNING_ID, notification) }
    }

    fun cancel(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }
}
