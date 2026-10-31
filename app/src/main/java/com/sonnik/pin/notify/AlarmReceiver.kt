package com.sonnik.pin.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sonnik.pin.PinApp
import com.sonnik.pin.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Будильник уведомлений и кнопка «Готово» в напоминании. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PinApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                        Notifier.cancel(context, id)
                        app.repository.setDone(id, true)
                    }
                    else -> {
                        Notifier.deliver(context, app.repository)
                        // Будильник ставится и на границу дня — заодно обновить виджет.
                        TodayWidget.refresh(context, app.repository)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sonnik.pin.action.FIRE"
        const val ACTION_DONE = "com.sonnik.pin.action.DONE"
        const val EXTRA_ID = "id"
    }
}

/** После перезагрузки, обновления и смены времени будильник надо поставить заново. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PinApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Notifier.deliver(context, app.repository)
            } finally {
                pending.finish()
            }
        }
    }
}
