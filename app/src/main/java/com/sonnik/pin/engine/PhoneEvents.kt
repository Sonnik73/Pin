package com.sonnik.pin.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Событие из календаря телефона (только чтение). Время — epoch millis, как в CalendarContract. */
data class PhoneEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    /** ARGB цвета события или календаря. */
    val color: Int? = null,
    val calendar: String = "",
)

/** Раскладка событий телефона по дням календаря (обычные даты, без границы логического дня). */
object PhoneEvents {
    /** Дни, которые занимает событие. События «на весь день» хранятся в UTC, конец — исключающий. */
    fun days(e: PhoneEvent, zone: ZoneId): LongRange {
        val z = if (e.allDay) ZoneOffset.UTC else zone
        val first = day(e.begin, z)
        val last = if (e.end > e.begin) day(e.end - 1, z) else first
        return first..maxOf(first, last)
    }

    fun byDay(events: List<PhoneEvent>, zone: ZoneId, from: Long, to: Long): Map<Long, List<PhoneEvent>> {
        val result = HashMap<Long, MutableList<PhoneEvent>>()
        for (e in events) {
            val span = days(e, zone)
            for (d in maxOf(span.first, from)..minOf(span.last, to)) result.getOrPut(d) { ArrayList() } += e
        }
        return result.mapValues { (_, list) ->
            list.distinctBy { it.id to it.begin }.sortedWith(compareBy({ !it.allDay }, { it.begin }, { it.title }))
        }
    }

    /** Время события в этот день: «весь день», «10:00–11:30», «с 22:00», «до 01:00». */
    fun describe(e: PhoneEvent, day: Long, zone: ZoneId): String {
        if (e.allDay) return "весь день"
        val span = days(e, zone)
        val start = minutes(e.begin, zone)
        val end = minutes(e.end, zone)
        return when {
            span.first == span.last -> if (e.end > e.begin) "${RuText.time(start)}–${RuText.time(end)}" else RuText.time(start)
            day == span.first -> "с ${RuText.time(start)}"
            day == span.last -> "до ${RuText.time(end)}"
            else -> "весь день"
        }
    }

    /** Отрезок времени для запроса: от начала первого дня до конца последнего с запасом на сутки (для «весь день» в UTC). */
    fun queryRange(from: Long, to: Long, zone: ZoneId): Pair<Long, Long> =
        LocalDate.ofEpochDay(from - 1).atStartOfDay(zone).toInstant().toEpochMilli() to
            LocalDate.ofEpochDay(to + 2).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun day(millis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    private fun minutes(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
}
