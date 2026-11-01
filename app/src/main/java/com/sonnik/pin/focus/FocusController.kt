package com.sonnik.pin.focus

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sonnik.pin.EXTRA_SCREEN
import com.sonnik.pin.MainActivity
import com.sonnik.pin.PinApp
import com.sonnik.pin.R
import com.sonnik.pin.engine.Focus
import com.sonnik.pin.engine.FocusPhase
import com.sonnik.pin.engine.FocusState
import com.sonnik.pin.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Фокус-таймер (помодоро). Состояние — в SharedPreferences, конец фазы — точный будильник, обратный отсчёт —
 * в постоянном уведомлении (системный хронометр, без фоновой службы). Минуты фокуса пишутся в журнал (`FOCUS`).
 */
class FocusController(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState?> = _state.asStateFlow()

    private fun load(): FocusState? {
        val phase = prefs.getString("phase", null)?.let { p -> FocusPhase.entries.firstOrNull { it.name == p } } ?: return null
        return FocusState(phase, prefs.getLong("started", 0), prefs.getLong("ends", 0), prefs.getInt("round", 0), prefs.getString("task", null))
    }

    private fun save(s: FocusState?) {
        prefs.edit().apply {
            if (s == null) {
                clear()
            } else {
                putString("phase", s.phase.name).putLong("started", s.startedAt).putLong("ends", s.endsAt)
                    .putInt("round", s.round).putString("task", s.taskId)
            }
        }.apply()
        _state.value = s
        if (s == null) cancelAlarm() else scheduleAlarm(s.endsAt)
        showNotification(s)
    }

    fun start(taskId: String?) {
        val round = _state.value?.round ?: 0
        save(Focus.start(System.currentTimeMillis(), taskId, round))
    }

    /** Остановить: отработанные минуты рабочей фазы засчитываются. */
    fun stop() {
        val s = _state.value ?: return
        log(s, Focus.finish(s, System.currentTimeMillis()).workedMinutes)
        save(null)
    }

    /** Будильник конца фазы. */
    fun onPhaseEnd() {
        val s = _state.value ?: return
        val now = System.currentTimeMillis()
        if (now + 1_000 < s.endsAt) {
            scheduleAlarm(s.endsAt)
            return
        }
        val step = Focus.finish(s, now)
        log(s, step.workedMinutes)
        save(step.next)
        if (step.next == null) showFinished()
    }

    private fun log(s: FocusState, minutes: Int) {
        if (s.phase != FocusPhase.WORK || minutes <= 0) return
        val repo = (context.applicationContext as PinApp).repository
        CoroutineScope(Dispatchers.Default).launch { repo.logFocus(s.taskId, minutes, s.startedAt) }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        41,
        Intent(context, FocusReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarm(at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
        }
    }

    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    private fun action(code: Int, action: String) = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, FocusReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openFocus() = PendingIntent.getActivity(
        context,
        44,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SCREEN, "focus"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun showNotification(s: FocusState?) {
        val nm = NotificationManagerCompat.from(context)
        if (s == null) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (!Notifier.canNotify(context)) return
        val title = when (s.phase) {
            FocusPhase.WORK -> "Фокус · ${s.minutes} мин"
            FocusPhase.BREAK -> "Перерыв · ${s.minutes} мин"
            FocusPhase.LONG_BREAK -> "Длинный перерыв · ${s.minutes} мин"
        }
        val task = s.taskId?.let { (context.applicationContext as PinApp).focusTaskTitle(it) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(task ?: "Помодоро")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(s.endsAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openFocus())
            .addAction(0, "Стоп", action(42, ACTION_STOP))
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    @SuppressLint("MissingPermission")
    private fun showFinished() {
        if (!Notifier.canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Перерыв окончен")
            .setContentText("Ещё один отрезок фокуса?")
            .setContentIntent(openFocus())
            .addAction(0, "Ещё 25 минут", action(43, ACTION_AGAIN))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    companion object {
        const val CHANNEL = "focus"
        const val CHANNEL_DONE = "focus_done"
        private const val NOTIFICATION_ID = 4
        const val ACTION_END = "com.sonnik.pin.focus.END"
        const val ACTION_STOP = "com.sonnik.pin.focus.STOP"
        const val ACTION_AGAIN = "com.sonnik.pin.focus.AGAIN"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Фокус-таймер", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Обратный отсчёт помодоро."
                    setSound(null, null)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Конец фокуса и перерыва", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Сигнал, когда отрезок закончился."
                },
            )
        }
    }
}

/** Будильник конца фазы и кнопки уведомления. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val focus = (context.applicationContext as PinApp).focus
        when (intent.action) {
            FocusController.ACTION_END -> {
                val before = focus.state.value?.phase
                focus.onPhaseEnd()
                // Конец работы — сигнал о перерыве.
                if (before == FocusPhase.WORK) notifyBreak(context)
            }
            FocusController.ACTION_STOP -> focus.stop()
            FocusController.ACTION_AGAIN -> focus.start(focus.state.value?.taskId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyBreak(context: Context) {
        if (!Notifier.canNotify(context)) return
        val n = NotificationCompat.Builder(context, FocusController.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Отрезок фокуса закончен")
            .setContentText("Время перерыва")
            .setTimeoutAfter(60_000)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(5, n) }
    }
}
