package com.sonnik.pin.engine

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Логический день: сутки начинаются не в полночь, а в [boundaryMinutes] (по умолчанию 04:00, как в Крепости).
 * Задача, отмеченная в 01:30, засчитывается вчерашнему дню; перенос невыполненного происходит в 04:00.
 *
 * День — номер от эпохи (LocalDate.toEpochDay), одинаковый с Крепостью.
 */
class LogicalDay(
    private val clock: Clock,
    val boundaryMinutes: Int = DEFAULT_BOUNDARY_MINUTES,
) {
    init {
        require(boundaryMinutes in 0 until MINUTES_PER_DAY) { "Граница дня должна быть в пределах суток: $boundaryMinutes" }
    }

    val zone: ZoneId get() = clock.zone

    /** Сегодняшний логический день. */
    fun today(): Long = dayOf(clock.millis())

    /** Логический день момента [epochMillis]. */
    fun dayOf(epochMillis: Long): Long = dayOf(epochMillis, zone, boundaryMinutes)

    /** Момент начала логического дня [day] (в epoch millis). */
    fun startOf(day: Long): Long =
        LocalDate.ofEpochDay(day).atStartOfDay(zone).plusMinutes(boundaryMinutes.toLong()).toInstant().toEpochMilli()

    /** Момент начала следующего логического дня — когда произойдёт следующий перенос. */
    fun nextBoundary(): Long = startOf(today() + 1)

    companion object {
        const val DEFAULT_BOUNDARY_MINUTES = 4 * 60
        const val MINUTES_PER_DAY = 24 * 60

        fun dayOf(epochMillis: Long, zone: ZoneId, boundaryMinutes: Int): Long =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
                .minusMinutes(boundaryMinutes.toLong())
                .toLocalDate()
                .toEpochDay()
    }
}
