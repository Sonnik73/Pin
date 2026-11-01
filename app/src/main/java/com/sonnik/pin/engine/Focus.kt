package com.sonnik.pin.engine

/** Фаза фокус-таймера. */
enum class FocusPhase(val title: String) { WORK("Фокус"), BREAK("Перерыв"), LONG_BREAK("Длинный перерыв") }

/** Состояние таймера: сохраняется целиком, поэтому переживает закрытие приложения и перезагрузку. */
data class FocusState(
    val phase: FocusPhase,
    val startedAt: Long,
    val endsAt: Long,
    /** Сколько рабочих отрезков завершено в этой серии. */
    val round: Int = 0,
    val taskId: String? = null,
) {
    val minutes: Int get() = ((endsAt - startedAt) / 60_000L).toInt()
    fun remainingMs(now: Long): Long = (endsAt - now).coerceAtLeast(0)
}

/** Настройки помодоро: 25 минут работы, 5 перерыв, каждый 4-й — 15. */
data class FocusConfig(val work: Int = 25, val shortBreak: Int = 5, val longBreak: Int = 15, val longEvery: Int = 4)

/** Итог завершения фазы: что дальше и сколько минут фокуса записать. */
data class FocusStep(val next: FocusState?, val workedMinutes: Int)

object Focus {
    private const val MIN = 60_000L

    fun start(now: Long, taskId: String?, round: Int = 0, config: FocusConfig = FocusConfig()) =
        FocusState(FocusPhase.WORK, now, now + config.work * MIN, round, taskId)

    /**
     * Фаза закончилась в [now] (или её завершили раньше). После работы — перерыв (каждый [FocusConfig.longEvery]-й —
     * длинный), минуты записываются; после перерыва — стоп, новый отрезок пользователь запускает сам.
     * Досрочно остановленная работа засчитывается целыми минутами, если прошла хотя бы минута.
     */
    fun finish(state: FocusState, now: Long, config: FocusConfig = FocusConfig()): FocusStep = when (state.phase) {
        FocusPhase.WORK -> {
            val worked = ((minOf(now, state.endsAt) - state.startedAt) / MIN).toInt()
            val round = state.round + 1
            val long = round % config.longEvery == 0
            val breakMinutes = if (long) config.longBreak else config.shortBreak
            val ended = now >= state.endsAt
            FocusStep(
                next = if (ended) {
                    FocusState(if (long) FocusPhase.LONG_BREAK else FocusPhase.BREAK, state.endsAt, state.endsAt + breakMinutes * MIN, round, state.taskId)
                } else {
                    null
                },
                workedMinutes = worked,
            )
        }
        else -> FocusStep(next = null, workedMinutes = 0)
    }

    /** «24:59». */
    fun format(ms: Long): String {
        val total = (ms + 999) / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
