package com.sonnik.pin

import android.app.Application
import com.sonnik.pin.data.PinDatabase
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.work.RolloverWorker

class PinApp : Application() {
    val repository: PinRepository by lazy { PinRepository(PinDatabase.create(this)) }

    override fun onCreate() {
        super.onCreate()
        // В юнит-тестах WorkManager может быть не инициализирован — расписание там не нужно.
        runCatching { RolloverWorker.schedule(this) }
    }
}
