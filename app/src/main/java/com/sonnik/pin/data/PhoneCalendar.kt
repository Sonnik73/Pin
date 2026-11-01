package com.sonnik.pin.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.sonnik.pin.engine.PhoneEvent
import com.sonnik.pin.engine.PhoneEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * События календаря телефона — только чтение, по желанию (настройка и разрешение READ_CALENDAR).
 * Pin ничего не пишет в календарь и не хранит события у себя.
 */
object PhoneCalendar {
    private const val PREFS = "phone_calendar"
    private const val KEY_ENABLED = "enabled"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Показывать ли события: включено в настройках и разрешение выдано. */
    fun isOn(context: Context): Boolean = isEnabled(context) && hasPermission(context)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** События, попадающие в дни [from]..[to]; при любой ошибке — пусто. */
    suspend fun between(context: Context, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): List<PhoneEvent> =
        withContext(Dispatchers.IO) {
            if (!isOn(context)) return@withContext emptyList()
            val (begin, end) = PhoneEvents.queryRange(from, to, zone)
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, begin)
                ContentUris.appendId(it, end)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            )
            val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
                "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED})"
            runCatching {
                context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                PhoneEvent(
                                    id = c.getLong(0),
                                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                                    begin = c.getLong(2),
                                    end = c.getLong(3),
                                    allDay = c.getInt(4) == 1,
                                    color = if (c.isNull(5)) null else c.getInt(5),
                                    calendar = c.getString(6).orEmpty(),
                                ),
                            )
                        }
                    }
                }.orEmpty()
            }.getOrDefault(emptyList())
        }

    /** Открыть событие в приложении календаря. */
    fun open(context: Context, e: PhoneEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
