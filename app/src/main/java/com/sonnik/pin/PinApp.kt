package com.sonnik.pin

import android.app.Application
import com.sonnik.pin.data.PinDatabase
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.ecosystem.PinProvider
import com.sonnik.pin.notify.Notifier
import com.sonnik.pin.sync.SyncWorker
import com.sonnik.pin.ui.ThemeSettings
import com.sonnik.pin.widget.TodayWidget
import com.sonnik.pin.work.RolloverWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class PinApp : Application() {
    val repository: PinRepository by lazy { PinRepository(PinDatabase.create(this)) }

    /** Оформление: режим, палитра, чёрный фон. */
    val themeSettings: ThemeSettings by lazy { ThemeSettings(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Запросы пересчитать уведомления: подряд идущие изменения схлопываются в один пересчёт. */
    private val changes = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        repository.onChanged = {
            changes.trySend(Unit)
            PinProvider.notifyChanged(this)
        }
        scope.launch {
            for (unit in changes) {
                runCatching { Notifier.deliver(this@PinApp, repository) }
                runCatching { TodayWidget.refresh(this@PinApp, repository) }
                runCatching { if (repository.settings().syncEnabled) SyncWorker.requestSoon(this@PinApp) }
            }
        }
        changes.trySend(Unit)
        // В юнит-тестах WorkManager может быть не инициализирован — расписание там не нужно.
        runCatching { RolloverWorker.schedule(this) }
        scope.launch { runCatching { if (repository.settings().syncEnabled) SyncWorker.schedule(this@PinApp) } }
    }
}
