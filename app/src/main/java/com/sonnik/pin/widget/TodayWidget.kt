package com.sonnik.pin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.sonnik.pin.MainActivity
import com.sonnik.pin.PinApp
import com.sonnik.pin.R
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TodayBoard
import com.sonnik.pin.notify.AlarmReceiver
import com.sonnik.pin.notify.Notifier
import com.sonnik.pin.ui.QuickAddActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Строка виджета. */
data class WidgetLine(val id: String, val title: String, val meta: String, val rolled: Boolean)

/** Что показать в виджете: открытые задачи дня (перенесённые сверху) и сколько не поместилось. */
fun widgetLines(board: TodayBoard, limit: Int): Pair<List<WidgetLine>, Int> {
    val open: List<TaskSnapshot> = board.rolledOver + board.today
    val lines = open.take(limit).map { t ->
        val meta = buildList {
            t.timeMinutes?.let { add(RuText.time(it)) }
            if (t.rollovers > 0) add("↷${t.rollovers}")
        }.joinToString(" · ")
        WidgetLine(t.id, t.title, meta, t.rollovers > 0)
    }
    return lines to (open.size - lines.size).coerceAtLeast(0)
}

/** Виджет «Сегодня»: задачи дня, отметка кружком, «+» — быстрое добавление. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                render(context, (context.applicationContext as PinApp).repository, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val LIMIT = 6

        /** Перерисовать все виджеты (после изменения данных и на границе дня). */
        suspend fun refresh(context: Context, repo: PinRepository) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) render(context, repo, manager, ids)
        }

        private suspend fun render(context: Context, repo: PinRepository, manager: AppWidgetManager, ids: IntArray) {
            repo.rollover()
            val today = repo.logicalDay().today()
            val open = repo.openDated().filter { !it.disabled }.mapNotNull { it.toSnapshot() }
            val board = TodayBoard.build(open, today, doneDayOf = { null })
            val (lines, more) = widgetLines(board, LIMIT)
            val (holidays, notices) = repo.occasionNotices(today)
            val event = (holidays + notices.map { com.sonnik.pin.engine.Occasions.describe(it) }).firstOrNull()

            val views = buildViews(context, today, board.openCount, lines, more, event)
            manager.updateAppWidget(ids, views)
        }

        /** Разметка виджета — отдельно от данных, чтобы рисовать её в скриншот-тесте. */
        fun buildViews(context: Context, today: Long, openCount: Int, lines: List<WidgetLine>, more: Int, event: String?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_title, RuText.headline(today))
            views.setTextViewText(R.id.widget_count, if (openCount == 0) "" else openCount.toString())
            views.setOnClickPendingIntent(R.id.widget_header, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_add, quickAdd(context))
            views.removeAllViews(R.id.widget_rows)
            for (line in lines) {
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.row_title, line.title)
                row.setTextViewText(R.id.row_meta, line.meta)
                row.setViewVisibility(R.id.row_meta, if (line.meta.isEmpty()) View.GONE else View.VISIBLE)
                row.setTextColor(R.id.row_meta, context.getColor(if (line.rolled) R.color.widget_accent else R.color.widget_muted))
                row.setOnClickPendingIntent(R.id.row_check, markDone(context, line.id))
                row.setOnClickPendingIntent(R.id.row_title, openApp(context))
                views.addView(R.id.widget_rows, row)
            }
            val footer = when {
                lines.isEmpty() -> "На сегодня всё 📌"
                more > 0 -> "и ещё $more"
                else -> ""
            }
            views.setTextViewText(R.id.widget_footer, footer)
            views.setViewVisibility(R.id.widget_footer, if (footer.isEmpty()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_event, event ?: "")
            views.setViewVisibility(R.id.widget_event, if (event == null) View.GONE else View.VISIBLE)
            return views
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun quickAdd(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun markDone(context: Context, taskId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            Notifier.notificationId(taskId) + 1,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DONE).putExtra(AlarmReceiver.EXTRA_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
