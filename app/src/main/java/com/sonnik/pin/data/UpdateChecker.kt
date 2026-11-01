package com.sonnik.pin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sonnik.pin.BuildConfigInfo
import com.sonnik.pin.engine.ReleaseInfo
import com.sonnik.pin.engine.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка обновлений: один запрос к GitHub за последним выпуском (без входа и ключей, репозиторий публичный).
 * Ничего о пользователе не отправляется — только обычный запрос страницы. Выключается в настройках.
 */
class UpdateChecker(private val context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    val currentVersion: String get() = BuildConfigInfo.versionName(context)

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

    /** Открыть APK (браузер скачает его) или, если файла нет, страницу выпуска. */
    fun download(release: ReleaseInfo) {
        val url = release.apkUrl ?: release.pageUrl
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
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
    }
}
