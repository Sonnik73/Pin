package com.sonnik.pin.ecosystem

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.sonnik.ecosystem.contract.EcosystemContract.Tasks
import com.sonnik.pin.BuildConfigInfo
import com.sonnik.pin.PinApp
import com.sonnik.pin.engine.EntryType
import kotlinx.coroutines.runBlocking

/**
 * Данные задач для приложений экосистемы (Крепость): только чтение, только цифры.
 * Доступ — по разрешению экосистемы (signature), см. AndroidManifest и docs/CONTRACT.md.
 */
class PinProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private val app: PinApp get() = context!!.applicationContext as PinApp

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val repo = app.repository
        val path = uri.pathSegments.singleOrNull() ?: throw IllegalArgumentException("Неизвестный путь: $uri")
        val cursor = runBlocking {
            // Перенос детерминирован: досчитываем, чтобы вчерашние долги попали в цифры даже без открытия Pin.
            repo.rollover()
            when (path) {
                Tasks.Info.PATH -> {
                    val settings = repo.settings()
                    rows(Tasks.Info.COLUMNS, projection) {
                        add(
                            mapOf(
                                Tasks.Info.SCHEMA_VERSION to Tasks.SCHEMA_VERSION,
                                Tasks.Info.APP_VERSION to BuildConfigInfo.versionName(context!!),
                                Tasks.Info.LAST_CHANGED_AT to settings.lastChangedAt,
                                Tasks.Info.DAY_BOUNDARY_MINUTES to settings.dayBoundaryMinutes,
                            ),
                        )
                    }
                }
                Tasks.DayStats.PATH -> {
                    val today = repo.logicalDay().today()
                    val to = uri.longParam(Tasks.PARAM_TO) ?: today
                    val from = uri.longParam(Tasks.PARAM_FROM) ?: (to - Tasks.DEFAULT_PERIOD_DAYS + 1)
                    val stats = if (from > to || to - from > MAX_DAYS) emptyList() else repo.dayStats(from, to)
                    rows(Tasks.DayStats.COLUMNS, projection) {
                        stats.forEach { d ->
                            add(
                                mapOf(
                                    Tasks.DayStats.DAY to d.day,
                                    Tasks.DayStats.PLANNED to d.planned,
                                    Tasks.DayStats.DONE to d.done,
                                    Tasks.DayStats.DONE_ON_TIME to d.doneOnTime,
                                    Tasks.DayStats.ROLLED_OVER to d.rolledOver,
                                    Tasks.DayStats.MISSED to d.missed,
                                    Tasks.DayStats.IMPORTANT_DONE to d.importantDone,
                                ),
                            )
                        }
                    }
                }
                Tasks.Completions.PATH -> {
                    val since = uri.longParam(Tasks.PARAM_SINCE) ?: (repo.now() - Tasks.DEFAULT_PERIOD_DAYS * DAY_MS)
                    val done = repo.completionsSince(since)
                    rows(Tasks.Completions.COLUMNS, projection) {
                        done.forEach { j ->
                            add(
                                mapOf(
                                    Tasks.Completions.COMPLETION_ID to j.id,
                                    Tasks.Completions.COMPLETED_AT to j.at,
                                    Tasks.Completions.DAY to j.day,
                                    Tasks.Completions.KIND to (EntryType.parse(j.kind) ?: EntryType.ONE_TIME).contractKind,
                                    Tasks.Completions.ON_TIME to if (j.onTime) 1 else 0,
                                    Tasks.Completions.ROLLOVERS to j.rollovers,
                                    Tasks.Completions.PRIORITY to j.priority,
                                ),
                            )
                        }
                    }
                }
                else -> throw IllegalArgumentException("Неизвестный путь: $uri")
            }
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, Uri.parse(Tasks.BASE_URI)) }
        return cursor
    }

    private fun Uri.longParam(name: String): Long? {
        val raw = getQueryParameter(name) ?: return null
        return raw.toLongOrNull() ?: throw IllegalArgumentException("Параметр $name должен быть числом: $raw")
    }

    /** Курсор с колонками из [projection] (в его порядке) или всеми [all]. */
    private inline fun rows(all: Array<String>, projection: Array<out String>?, fill: MutableList<Map<String, Any?>>.() -> Unit): MatrixCursor {
        val columns = projection?.toList() ?: all.toList()
        columns.firstOrNull { it !in all }?.let { throw IllegalArgumentException("Неизвестная колонка: $it") }
        val data = ArrayList<Map<String, Any?>>().apply(fill)
        return MatrixCursor(columns.toTypedArray(), data.size).apply {
            data.forEach { row -> addRow(columns.map { row[it] }) }
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Только чтение")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Только чтение")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Только чтение")

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        /** Больше двух лет за раз не отдаём — защита от случайно огромного запроса. */
        private const val MAX_DAYS = 800L

        /** Сообщить читателям, что данные изменились. */
        fun notifyChanged(context: Context) {
            runCatching { context.contentResolver.notifyChange(Uri.parse(Tasks.BASE_URI), null) }
        }
    }
}
