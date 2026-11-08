package com.sonnik.pin.engine

/** Тип записи. Имена enum попадают в базу, .md-файлы и контракт — не переименовывать. */
enum class EntryType {
    ONE_TIME,
    RECURRING,
    BIRTHDAY,
    HOLIDAY,
    PLAN,
    GIFT,

    /** Привычка: отмечается за день, без переноса; серии и тепловая карта. */
    HABIT;

    /** Значение `kind` для контракта с Крепостью. */
    val contractKind: String get() = name.lowercase()

    companion object {
        fun parse(value: String?): EntryType? = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Три уровня приоритета. `level` хранится в базе и файлах: 0 — низкий, 1 — обычный, 2 — важный. */
enum class Priority(val level: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    companion object {
        fun ofLevel(level: Int?): Priority = entries.firstOrNull { it.level == level } ?: NORMAL

        fun parse(value: String?): Priority {
            val v = value?.trim()?.lowercase() ?: return NORMAL
            v.toIntOrNull()?.let { return ofLevel(it) }
            return when (v) {
                "low", "низкий" -> LOW
                "high", "important", "важный", "высокий" -> HIGH
                else -> NORMAL
            }
        }
    }
}

/** Состояние задачи. */
enum class TaskStatus {
    /** Ждёт выполнения. */
    OPEN,
    /** Выполнена. */
    DONE,
    /** Пропущена: повтор периодической задачи закрылся, когда наступил следующий. */
    MISSED;

    companion object {
        fun parse(value: String?): TaskStatus = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: OPEN
    }
}

/**
 * То, что ядру нужно знать о задаче с датой. Дни — логические эпохальные дни (см. [LogicalDay]).
 *
 * @property originalDay день, на который задачу поставили изначально; при переносах не меняется.
 * @property currentDay день, на котором задача стоит сейчас (после переносов).
 * @property timeMinutes время дня в минутах от полуночи или null, если задача без времени.
 */
data class TaskSnapshot(
    val id: String,
    val type: EntryType,
    val status: TaskStatus,
    val originalDay: Long,
    val currentDay: Long,
    val timeMinutes: Int? = null,
    val priority: Priority = Priority.NORMAL,
    val rollovers: Int = 0,
    val createdAt: Long = 0L,
    val title: String = "",
    /** Напоминания: за сколько минут до времени задачи (0 — в само время); пусто — не напоминать. */
    val reminders: List<Int> = listOf(0),
    /** «Отложить» из уведомления: напомнить снова не раньше этого момента (epoch millis). */
    val snoozeUntil: Long? = null,
)
