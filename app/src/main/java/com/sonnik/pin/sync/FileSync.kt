package com.sonnik.pin.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonnik.pin.PinApp
import com.sonnik.pin.data.Archive
import com.sonnik.pin.data.PinRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Состояние хранения одним файлом: где файл и как прошла последняя синхронизация. */
data class FileSyncState(val uri: String? = null, val lastAt: Long = 0, val error: String? = null) {
    val enabled: Boolean get() = uri != null
}

/**
 * Хранение одним zip-файлом через системное окно «Сохранить в…» — например, на Google Диске через его приложение.
 * Не нужны ни вход в Google, ни Google Cloud: файл отправляет в облако само приложение Диска.
 * Настройки — на устройстве (SharedPreferences), в синхронизацию не попадают.
 */
class FileSyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences("file_sync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<FileSyncState> = _state.asStateFlow()

    private fun read() = FileSyncState(
        uri = prefs.getString(KEY_URI, null),
        lastAt = prefs.getLong(KEY_LAST, 0),
        error = prefs.getString(KEY_ERROR, null),
    )

    fun update(change: (FileSyncState) -> FileSyncState) {
        val s = change(_state.value)
        prefs.edit().putString(KEY_URI, s.uri).putLong(KEY_LAST, s.lastAt).putString(KEY_ERROR, s.error).apply()
        _state.value = s
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_LAST = "last_at"
        const val KEY_ERROR = "error"
    }
}

/** Итог синхронизации с файлом. [repaired] — файл был испорчен и заменён данными этого телефона. */
data class FileSyncReport(val imported: PinRepository.ImportReport?, val written: Boolean, val repaired: Boolean = false)

/**
 * Слияние с файлом: прочитать архив (правки с другого телефона) → слить с базой (новее — побеждает, журнал без дублей)
 * → записать актуальный архив, если он изменился. Чтение и запись — функциями, чтобы проверять без Android.
 */
object FileSyncEngine {
    /** То же над байтами — для проверок; внутри работает через временные файлы. */
    suspend fun sync(repo: PinRepository, read: suspend () -> ByteArray?, write: suspend (ByteArray) -> Unit): FileSyncReport {
        val tmp = java.nio.file.Files.createTempDirectory("pin-sync").toFile()
        try {
            return syncFiles(
                repo, tmp,
                readInto = { f -> read()?.let { f.writeBytes(it); true } ?: false },
                write = { f -> write(f.readBytes()) },
                readBack = { read()?.let(::md5) },
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    /**
     * Синхронизация через временные файлы в [tmpDir]: архив и вложения идут потоком, в памяти не копятся.
     * - [readInto] кладёт текущий Pin.zip во временный файл (false — файла ещё нет).
     * - Испорченный архив (оборванная запись) не останавливает синхронизацию: данные этого телефона полные,
     *   они и записываются — файл чинится сам.
     * - После записи [readBack] (если задан) сверяет файл с записанным; не совпало — запись повторяется один раз.
     */
    suspend fun syncFiles(
        repo: PinRepository,
        tmpDir: File,
        readInto: suspend (File) -> Boolean,
        write: suspend (File) -> Unit,
        readBack: (suspend () -> String?)? = null,
    ): FileSyncReport {
        tmpDir.mkdirs()
        val current = File(tmpDir, "pin-current.zip")
        val fresh = File(tmpDir, "pin-fresh.zip")
        try {
            val has = readInto(current) && current.length() > 0
            var repaired = false
            val imported = if (has) {
                try {
                    if (!Archive.isComplete(current)) throw java.util.zip.ZipException("архив оборван")
                    val content = current.inputStream().buffered().use { input ->
                        Archive.readContent(input, onBlob = { path, stream -> repo.importBlob(path, stream) })
                    }
                    if (content.files.isEmpty()) null else repo.importFiles(content.files)
                } catch (e: java.io.IOException) {
                    // Читаем свой временный файл, так что ошибка чтения — это испорченный архив (ZipException, обрыв).
                    repaired = true
                    null
                }
            } else {
                null
            }
            runCatching { repo.pruneAttachments() }
            // Вложения едут в том же архиве (папка attachments/).
            fresh.outputStream().buffered().use { out ->
                Archive.writeSources(out, repo.exportFiles(includeDeleted = true), repo.blobFiles().map { (path, f) -> path to { f.inputStream() } })
            }
            val changed = !has || md5(current) != md5(fresh)
            if (changed) {
                write(fresh)
                if (readBack != null) {
                    val expected = md5(fresh)
                    if (readBack() != expected) {
                        write(fresh)
                        if (readBack() != expected) error("файл после записи не совпадает с записанным")
                    }
                }
            }
            return FileSyncReport(imported, changed, repaired)
        } finally {
            current.delete()
            fresh.delete()
        }
    }

    fun md5(file: File): String = file.inputStream().use(::md5)

    fun md5(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** Синхронизация с файлом в фоне: при запуске, вскоре после изменений и раз в несколько часов. */
class FileSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PinApp
        val settings = app.fileSync
        val uri = settings.state.value.uri?.let(Uri::parse) ?: return Result.success()
        val resolver = applicationContext.contentResolver
        return try {
            FileSyncEngine.syncFiles(
                app.repository,
                File(applicationContext.cacheDir, "file-sync"),
                readInto = { f ->
                    withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) }; true } ?: false
                    }
                },
                write = { f ->
                    withContext(Dispatchers.IO) {
                        // «wt» — перезаписать целиком (обрезать старое содержимое); после записи — сверка.
                        val out = resolver.openOutputStream(uri, "wt") ?: error("Файл недоступен для записи")
                        out.use { o -> f.inputStream().use { it.copyTo(o) } }
                    }
                },
                readBack = { withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use(FileSyncEngine::md5) } },
            )
            settings.update { it.copy(lastAt = System.currentTimeMillis(), error = null) }
            Result.success()
        } catch (e: SecurityException) {
            settings.update { it.copy(error = "Нет доступа к файлу — выберите его заново") }
            Result.success()
        } catch (e: Exception) {
            settings.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "file-sync-periodic"
        private const val SOON = "file-sync-soon"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FileSyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun requestSoon(context: Context, delaySeconds: Long = 20) {
            val request = OneTimeWorkRequestBuilder<FileSyncWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(SOON)
        }

        /** Запомнить выбранный файл: доступ сохраняется и после перезапуска телефона. */
        fun attach(context: Context, uri: Uri) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            (context.applicationContext as PinApp).fileSync.update { FileSyncState(uri = uri.toString()) }
            schedule(context)
            requestSoon(context, delaySeconds = 0)
        }

        fun detach(context: Context) {
            val app = context.applicationContext as PinApp
            app.fileSync.state.value.uri?.let { u ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(u),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            app.fileSync.update { FileSyncState() }
            cancel(context)
        }
    }
}
