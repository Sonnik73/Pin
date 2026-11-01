package com.sonnik.pin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.sonnik.pin.EXTRA_SCREEN
import com.sonnik.pin.MainActivity
import com.sonnik.pin.PinApp
import com.sonnik.pin.R
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.DayMarks
import com.sonnik.pin.engine.MarkKind
import com.sonnik.pin.ui.QuickAddActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Виджет «Календарь»: месяц с теми же значками, что на вкладке «Календарь» (точки задач, «!» — важная,
 * 🎂 — день рождения, красное число — праздник). ‹ › листают месяцы, нажатие на день открывает его в Pin.
 */
class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        launch(context) { render(context, (context.applicationContext as PinApp).repository, manager, ids) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val shift = when (intent.action) {
            ACTION_PREV -> -1
            ACTION_NEXT -> 1
            ACTION_TODAY -> 0
            else -> null
        }
        if (shift == null || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            super.onReceive(context, intent)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val offset = if (shift == 0) 0 else prefs.getInt(key(id), 0) + shift
        prefs.edit().putInt(key(id), offset).apply()
        launch(context) {
            render(context, (context.applicationContext as PinApp).repository, AppWidgetManager.getInstance(context), intArrayOf(id))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    private fun launch(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val PREFS = "calendar_widget"
        private const val ACTION_PREV = "com.sonnik.pin.widget.CAL_PREV"
        private const val ACTION_NEXT = "com.sonnik.pin.widget.CAL_NEXT"
        private const val ACTION_TODAY = "com.sonnik.pin.widget.CAL_TODAY"
        private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        private val MONTHS = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        )

        private fun key(id: Int) = "offset_$id"

        /** Перерисовать все виджеты календаря (после изменения данных и на границе дня). */
        suspend fun refresh(context: Context, repo: PinRepository) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isNotEmpty()) render(context, repo, manager, ids)
        }

        private suspend fun render(context: Context, repo: PinRepository, manager: AppWidgetManager, ids: IntArray) {
            val today = repo.logicalDay().today()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            for (id in ids) {
                val month = LocalDate.ofEpochDay(today).withDayOfMonth(1).plusMonths(prefs.getInt(key(id), 0).toLong())
                val from = month.toEpochDay()
                val to = month.withDayOfMonth(month.lengthOfMonth()).toEpochDay()
                manager.updateAppWidget(id, buildViews(context, id, month, today, repo.monthMarks(from, to)))
            }
        }

        /** Разметка — отдельно от данных, чтобы рисовать её в скриншот-тесте. */
        fun buildViews(context: Context, widgetId: Int, month: LocalDate, today: Long, marks: Map<Long, DayMarks>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val first = month.withDayOfMonth(1)
            val isCurrent = first == LocalDate.ofEpochDay(today).withDayOfMonth(1)
            views.setTextViewText(R.id.cal_title, "${MONTHS[first.monthValue - 1]} ${first.year}" + if (isCurrent) "" else " ↩")
            views.setOnClickPendingIntent(R.id.cal_prev, shift(context, widgetId, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.cal_next, shift(context, widgetId, ACTION_NEXT))
            // Заголовок: в чужом месяце — вернуться к текущему, в текущем — открыть календарь в Pin.
            views.setOnClickPendingIntent(R.id.cal_title, if (isCurrent) openDay(context, today) else shift(context, widgetId, ACTION_TODAY))
            views.setOnClickPendingIntent(R.id.cal_add, quickAdd(context))

            views.removeAllViews(R.id.cal_weekdays)
            WEEKDAYS.forEachIndexed { i, name ->
                val w = RemoteViews(context.packageName, R.layout.widget_cal_weekday)
                w.setTextViewText(R.id.weekday, name)
                w.setTextColor(R.id.weekday, context.getColor(if (i >= 5) R.color.widget_holiday else R.color.widget_muted))
                views.addView(R.id.cal_weekdays, w)
            }

            views.removeAllViews(R.id.cal_rows)
            val offset = first.dayOfWeek.value - 1
            val days = first.lengthOfMonth()
            val weeks = (offset + days + 6) / 7
            for (w in 0 until weeks) {
                val row = RemoteViews(context.packageName, R.layout.widget_cal_week)
                for (c in 0 until 7) {
                    val dom = w * 7 + c - offset + 1
                    val cell = RemoteViews(context.packageName, R.layout.widget_cal_cell)
                    if (dom in 1..days) {
                        val day = first.withDayOfMonth(dom).toEpochDay()
                        val m = marks[day]
                        val isToday = day == today
                        cell.setTextViewText(R.id.cell_day, dom.toString())
                        cell.setTextColor(
                            R.id.cell_day,
                            when {
                                isToday -> 0xFFFFFFFF.toInt()
                                m?.holiday == true -> context.getColor(R.color.widget_holiday)
                                else -> context.getColor(R.color.widget_text)
                            },
                        )
                        if (isToday) cell.setInt(R.id.cell_day, "setBackgroundResource", R.drawable.widget_today_bg)
                        cell.setTextViewText(R.id.cell_marks, marksText(context, m))
                        cell.setOnClickPendingIntent(R.id.cell, openDay(context, day))
                    } else {
                        cell.setTextViewText(R.id.cell_day, "")
                        cell.setTextViewText(R.id.cell_marks, "")
                    }
                    row.addView(R.id.week_row, cell)
                }
                views.addView(R.id.cal_rows, row)
            }
            return views
        }

        /** «!••🎂» цветными знаками: «!» — важная, точки — задачи/повторы/сделанное. */
        private fun marksText(context: Context, m: DayMarks?): CharSequence {
            if (m == null) return " "
            val b = SpannableStringBuilder()
            fun put(text: String, color: Int, bold: Boolean = false) {
                val start = b.length
                b.append(text)
                b.setSpan(ForegroundColorSpan(color), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (bold) b.setSpan(StyleSpan(Typeface.BOLD), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (m.important) put("!", context.getColor(R.color.widget_holiday), bold = true)
            m.dots.forEach { kind ->
                put(
                    "•",
                    context.getColor(
                        when (kind) {
                            MarkKind.OPEN -> R.color.widget_task
                            MarkKind.PROJECTED -> R.color.widget_accent
                            MarkKind.DONE -> R.color.widget_muted
                        },
                    ),
                )
            }
            if (m.birthday) b.append("🎂")
            return if (b.isEmpty()) " " else b
        }

        private fun shift(context: Context, widgetId: Int, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId * 10 + when (action) {
                ACTION_PREV -> 1
                ACTION_NEXT -> 2
                else -> 3
            },
            Intent(context, CalendarWidget::class.java).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Открыть Pin на вкладке «Календарь» с выбранным днём. */
        private fun openDay(context: Context, day: Long): PendingIntent = PendingIntent.getActivity(
            context,
            20_000 + (day % 50_000).toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SCREEN, "calendar:$day"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun quickAdd(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
