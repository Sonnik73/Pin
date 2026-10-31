package com.sonnik.pin.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonnik.pin.PinApp
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Синхронизация с Google Диском в фоне: раз в несколько часов и вскоре после изменений. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as PinApp).repository
        if (!repo.settings().syncEnabled) return Result.success()
        return try {
            val token = withContext(Dispatchers.IO) { DriveAuth.tokenBlocking(applicationContext) }
            SyncEngine(repo, DriveRest(token), ZoneId.systemDefault()).sync()
            Result.success()
        } catch (e: NeedsSignInException) {
            repo.updateSettingsQuietly { it.copy(lastSyncError = e.message) }
            Result.success()
        } catch (e: IOException) {
            repo.updateSettingsQuietly { it.copy(lastSyncError = "Нет связи с Google Диском: ${e.message?.take(120)}") }
            if (runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            repo.updateSettingsQuietly { it.copy(lastSyncError = e.message ?: e.javaClass.simpleName) }
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "sync-periodic"
        private const val SOON = "sync-soon"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Синхронизировать вскоре: изменения подряд схлопываются в одну отправку. */
        fun requestSoon(context: Context, delaySeconds: Long = 30) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(SOON)
        }
    }
}
