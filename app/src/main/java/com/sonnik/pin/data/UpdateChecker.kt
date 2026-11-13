package com.sonnik.pin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sonnik.pin.BuildConfigInfo
import com.sonnik.pin.engine.ReleaseInfo
import com.sonnik.pin.engine.Updates
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка обновлений: один запрос к GitHub за последним выпуском (без входа и ключей, репозиторий публичный).
 * Ничего о пользователе не отправляется — только обычный запрос страницы. Выключается в настройках.
 * Новая версия скачивается в фон в файлы приложения и ставится штатным установщиком Android.
 */
class UpdateChecker(private val context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    val currentVersion: String get() = BuildConfigInfo.versionName(context)

    /** Версия скачанного и ждущего установки APK (null — скачивания не было). Живёт между запусками. */
    var pendingVersion: String?
        get() = prefs.getString(KEY_PENDING, null)
        private set(value) {
            prefs.edit().putString(KEY_PENDING, value).apply()
        }

    /** Результат ручной проверки. */
    sealed interface Result {
        data class Available(val release: ReleaseInfo) : Result
        data class UpToDate(val latest: ReleaseInfo) : Result
        data class Failed(val reason: String) : Result
    }

    /** При входе в приложение: не чаще раза в 6 часов, без пропущенной версии; ошибки молча. */
    suspend fun checkIfDue(now: Long = System.currentTimeMillis()): ReleaseInfo? {
        if (!enabled || !Updates.isDue(prefs.getLong(KEY_LAST, 0), now)) return null
        val release = runCatching { fetch() }.getOrNull() ?: return null
        prefs.edit().putLong(KEY_LAST, now).apply()
        return release.takeIf { Updates.shouldOffer(it, currentVersion, prefs.getString(KEY_SKIPPED, null)) }
    }

    /** Кнопка «Проверить сейчас»: всегда спрашивает GitHub и показывает даже пропущенную версию. */
    suspend fun checkNow(): Result = runCatching { fetch() }.fold(
        onSuccess = { r ->
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            if (Updates.isNewer(r.version, currentVersion)) Result.Available(r) else Result.UpToDate(r)
        },
        onFailure = { Result.Failed(it.message ?: it.javaClass.simpleName) },
    )

    fun skip(release: ReleaseInfo) {
        prefs.edit().putString(KEY_SKIPPED, release.version).apply()
    }

    /** Скачанный файл версии [version], если он цел; битые и устаревшие файлы подчищает. */
    fun downloaded(version: String): File? {
        val dir = File(context.filesDir, UPDATES_DIR)
        val target = File(dir, Updates.apkName(version))
        // Остатки других версий не копятся: обновились — старый APK больше не нужен.
        dir.listFiles()?.forEach { if (it.isFile && it != target) it.delete() }
        return target.takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Скачать APK в файлы приложения (фоном); прогресс — байтами в [onProgress].
     * null — не скачалось (нет сети, оборвалось, размер не сошёлся с GitHub). Частичный файл удаляется.
     */
    suspend fun download(release: ReleaseInfo, onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> }): File? =
        withContext(Dispatchers.IO) {
            val url = release.apkUrl ?: return@withContext null
            val dir = File(context.filesDir, UPDATES_DIR).apply { mkdirs() }
            val target = File(dir, Updates.apkName(release.version))
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "Pin-Android")
                if (connection.responseCode != 200) return@withContext null
                val total = connection.contentLengthLong.takeIf { it > 0 }
                try {
                    connection.inputStream.use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                onProgress(read, total)
                            }
                        }
                    }
                } catch (e: Exception) {
                    target.delete()
                    return@withContext null
                }
            } finally {
                connection.disconnect()
            }
            // Размер из GitHub — контроль целостности скачивания.
            val sizeOk = release.apkBytes == null || target.length() == release.apkBytes
            if (!sizeOk || target.length() == 0L) {
                target.delete()
                return@withContext null
            }
            pendingVersion = release.version
            target
        }

    /** Открыть штатный установщик Android со скачанным APK. Без разрешения «неизвестные источники» — в настройки. */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Обновились или файл потерялся — ожидание установки больше неактуально. */
    fun clearPendingIfStale(version: String? = pendingVersion) {
        if (version == null) return
        if (!Updates.isNewer(version, currentVersion) || downloaded(version) == null) pendingVersion = null
    }

    private suspend fun fetch(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = URL(Updates.LATEST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Pin-Android")
            when (val code = connection.responseCode) {
                200 -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                404 -> error("выпуски не найдены — репозиторий закрыт или выпусков нет")
                403, 429 -> error("GitHub временно ограничил запросы, попробуйте позже")
                else -> error("ответ GitHub $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): ReleaseInfo {
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets")
        val apkAsset = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val apk = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
        val published = o.optString("published_at").takeIf { it.isNotBlank() }?.let {
            runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() }.getOrNull()
        }
        return ReleaseInfo(
            version = o.getString("tag_name"),
            pageUrl = o.optString("html_url").ifBlank { "https://github.com/Sonnik73/Pin/releases/latest" },
            apkUrl = apk,
            notes = Updates.cleanNotes(o.optString("body")),
            publishedDay = published,
            apkBytes = apkAsset?.optLong("size")?.takeIf { it > 0 },
        )
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST = "last_check"
        const val KEY_SKIPPED = "skipped"
        const val KEY_PENDING = "pending"
        const val UPDATES_DIR = "updates"
    }
}
