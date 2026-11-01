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
import android.util.TypedValue
import android.widget.RemoteViews
import com.sonnik.pin.EXTRA_SCREEN
import com.sonnik.pin.MainActivity
import com.sonnik.pin.PinApp
import com.sonnik.pin.R
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.CalendarEvent
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

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        // Размер виджета поменяли — пересчитать шрифт.
        launch(context) { render(context, (context.applicationContext as PinApp).repository, manager, intArrayOf(id)) }
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

        fun weeksIn(month: LocalDate): Int {
            val first = month.withDayOfMonth(1)
            return (first.dayOfWeek.value - 1 + first.lengthOfMonth() + 6) / 7
        }

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
                val marks = repo.monthMarks(from, to)
                val events = CalendarEvent.upcoming(repo.monthEvents(from, to), fromDay = maxOf(from, today))
                manager.updateAppWidget(
                    id,
                    sizedViews(manager, id) { width, height ->
                        buildViews(context, id, month, today, marks, CalendarSizes.of(width, height, weeksIn(month), events.size), events)
                    },
                )
            }
        }

        /** Разметка — отдельно от данных, чтобы рисовать её в скриншот-тесте. */
        fun buildViews(
            context: Context,
            widgetId: Int,
            month: LocalDate,
            today: Long,
            marks: Map<Long, DayMarks>,
            sizes: CalendarSizes = CalendarSizes.of(300, 300, weeksIn(month)),
            events: List<CalendarEvent> = emptyList(),
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            views.setInt(R.id.cal_bg, "setImageAlpha", WidgetLook.alpha(context))
            views.setTextViewTextSize(R.id.cal_title, TypedValue.COMPLEX_UNIT_SP, sizes.title)
            views.setTextViewTextSize(R.id.cal_prev, TypedValue.COMPLEX_UNIT_SP, sizes.title + 7)
            views.setTextViewTextSize(R.id.cal_next, TypedValue.COMPLEX_UNIT_SP, sizes.title + 7)
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
                w.setTextViewTextSize(R.id.weekday, TypedValue.COMPLEX_UNIT_SP, sizes.weekday)
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
                        cell.setTextViewTextSize(R.id.cell_day, TypedValue.COMPLEX_UNIT_SP, sizes.day)
                        cell.setTextViewTextSize(R.id.cell_marks, TypedValue.COMPLEX_UNIT_SP, sizes.marks)
                        if (sizes.compact) {
                            // Низкая клетка: «14🎂» в одну строку, значки мельче числа.
                            val text = SpannableStringBuilder(dom.toString())
                            val marksText = marksText(context, m, compact = true, onDark = isToday).takeIf { it.isNotBlank() }
                            if (marksText != null) {
                                val start = text.length
                                text.append(marksText)
                                text.setSpan(android.text.style.RelativeSizeSpan(0.65f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            cell.setTextViewText(R.id.cell_day, text)
                            cell.setInt(R.id.cell_day, "setMaxLines", 1)
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                cell.setViewLayoutWidth(R.id.cell_day, android.view.ViewGroup.LayoutParams.WRAP_CONTENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                                cell.setViewLayoutHeight(R.id.cell_day, android.view.ViewGroup.LayoutParams.WRAP_CONTENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                            }
                            cell.setViewVisibility(R.id.cell_marks, android.view.View.GONE)
                        } else {
                            cell.setTextViewText(R.id.cell_day, dom.toString())
                            cell.setViewPadding(R.id.cell_day, 0, 0, 0, 0)
                            // Кружок «сегодня» растёт вместе с числом (размер вида задаётся только с Android 12).
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                cell.setViewLayoutWidth(R.id.cell_day, sizes.circle, TypedValue.COMPLEX_UNIT_DIP)
                                cell.setViewLayoutHeight(R.id.cell_day, sizes.circle, TypedValue.COMPLEX_UNIT_DIP)
                            }
                        }
                        cell.setTextColor(
                            R.id.cell_day,
                            when {
                                isToday -> 0xFFFFFFFF.toInt()
                                m?.holiday == true -> context.getColor(R.color.widget_holiday)
                                else -> context.getColor(R.color.widget_text)
                            },
                        )
                        if (isToday) cell.setInt(R.id.cell_day, "setBackgroundResource", R.drawable.widget_today_bg)
                        if (!sizes.compact) cell.setTextViewText(R.id.cell_marks, marksText(context, m))
                        cell.setOnClickPendingIntent(R.id.cell, openDay(context, day))
                    } else {
                        cell.setTextViewText(R.id.cell_day, "")
                        cell.setTextViewText(R.id.cell_marks, "")
                    }
                    row.addView(R.id.week_row, cell)
                }
                views.addView(R.id.cal_rows, row)
            }
            // Подписи: дни рождения и праздники месяца, ближайшие сначала (сколько помещается).
            val shown = events.take(sizes.eventLines)
            views.setTextViewText(R.id.cal_events, shown.joinToString("\n") { it.label() })
            views.setTextViewTextSize(R.id.cal_events, TypedValue.COMPLEX_UNIT_SP, sizes.event)
            views.setInt(R.id.cal_events, "setMaxLines", maxOf(1, sizes.eventLines))
            views.setViewVisibility(R.id.cal_events, if (shown.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            return views
        }

        /** «!••🎂» цветными знаками: «!» — важная, точки — задачи/повторы/сделанное. */
        /**
         * [compact] — значки рядом с числом в узкой клетке: не больше двух точек, а при 🎂 — только тортик.
         * [onDark] — на кружке «сегодня»: всё белым, иначе точки сливаются с фоном.
         */
        private fun marksText(context: Context, m: DayMarks?, compact: Boolean = false, onDark: Boolean = false): CharSequence {
            if (m == null) return " "
            val white = 0xFFFFFFFF.toInt()
            val b = SpannableStringBuilder()
            fun put(text: String, color: Int, bold: Boolean = false) {
                val start = b.length
                b.append(text)
                b.setSpan(ForegroundColorSpan(color), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (bold) b.setSpan(StyleSpan(Typeface.BOLD), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (m.important) put("!", if (onDark) white else context.getColor(R.color.widget_holiday), bold = true)
            val dots = when {
                !compact -> m.dots
                m.birthday -> emptyList()
                else -> m.dots.take(2)
            }
            dots.forEach { kind ->
                put(
                    "●",
                    if (onDark) {
                        white
                    } else {
                        context.getColor(
                            when (kind) {
                                MarkKind.OPEN -> R.color.widget_task
                                MarkKind.PROJECTED -> R.color.widget_accent
                                MarkKind.DONE -> R.color.widget_muted
                            },
                        )
                    },
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

/**
 * Размеры шрифта виджета под его размер: число дня — по высоте и ширине клетки, остальное — от него.
 * Чистая функция — проверяется тестом.
 */
data class CalendarSizes(
    val day: Float,
    val marks: Float,
    val weekday: Float,
    val title: Float,
    val circle: Float,
    /** Шрифт подписей дней рождения и праздников и сколько их помещается внизу. */
    val event: Float = 12f,
    val eventLines: Int = 0,
    /** Клетки низкие: значки — рядом с числом, а не под ним (иначе обрезаются). */
    val compact: Boolean = false,
) {
    companion object {
        /** [widthDp]×[heightDp] — размер виджета, [weeks] — сколько недель в месяце (4–6). */
        fun of(widthDp: Int, heightDp: Int, weeks: Int, events: Int = 0): CalendarSizes {
            // Подписи внизу: чем выше виджет, тем больше строк (до трёх); на совсем низком — ни одной.
            val event = (widthDp / 26f).coerceIn(11f, 15f)
            val maxLines = when {
                heightDp >= 300 -> 3
                heightDp >= 230 -> 2
                heightDp >= 170 -> 1
                else -> 0
            }
            val eventLines = minOf(events, maxLines)
            val reserved = if (eventLines == 0) 0f else eventLines * event * 1.45f + 6f
            // Поля, заголовок и строка дней недели занимают около 80 dp.
            val cellH = (heightDp - 80f - reserved).coerceAtLeast(60f) / weeks.coerceAtLeast(4)
            val cellW = (widthDp - 20f).coerceAtLeast(140f) / 7
            val day = minOf(cellH * 0.4f, cellW * 0.4f).coerceIn(11f, 26f)
            val marks = (day * 0.65f).coerceIn(8f, 15f)
            return CalendarSizes(
                day = day,
                marks = marks,
                weekday = (day * 0.75f).coerceIn(11f, 17f),
                title = (day * 1.05f).coerceIn(15f, 24f),
                // Кружок + строка значков должны поместиться в клетку.
                // 🎂 выше обычного текста — на строку значков закладываем 1,8 размера шрифта.
                circle = (day * 1.7f).coerceAtMost(cellH - marks * 1.8f).coerceAtLeast(day * 1.45f),
                event = event,
                eventLines = eventLines,
                // Кружок должен вместить двузначное число (≈ 1,45 размера шрифта) и оставить место значкам.
                compact = cellH - marks * 1.8f < day * 1.45f,
            )
        }
    }
}
