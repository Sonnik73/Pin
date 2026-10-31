package com.sonnik.pin.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonnik.pin.PinApp
import java.util.concurrent.TimeUnit

/**
 * Перенос невыполненного в фоне. Перенос детерминирован и досчитывается при каждом открытии приложения,
 * так что фон нужен лишь затем, чтобы данные были свежими для Крепости и виджета, даже если Pin не открывали.
 */
class RolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as PinApp).repository.rollover()
        return Result.success()
    }

    companion object {
        private const val NAME = "rollover"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RolloverWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
