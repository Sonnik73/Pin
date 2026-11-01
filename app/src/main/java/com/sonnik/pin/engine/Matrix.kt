package com.sonnik.pin.engine

/** Квадрант матрицы Эйзенхауэра. */
enum class Quadrant(val title: String, val hint: String, val urgent: Boolean, val important: Boolean) {
    DO("Сделать сейчас", "срочно и важно", urgent = true, important = true),
    SCHEDULE("Запланировать", "важно, не срочно", urgent = false, important = true),
    QUICK("Сделать быстро", "срочно, не важно", urgent = true, important = false),
    LATER("Отложить или убрать", "не срочно и не важно", urgent = false, important = false),
    ;

    companion object {
        fun of(urgent: Boolean, important: Boolean): Quadrant = entries.first { it.urgent == urgent && it.important == important }
    }
}

/** Задача для матрицы: день (null — план без даты). */
data class MatrixItem(val id: String, val type: EntryType, val day: Long?, val priority: Priority, val rollovers: Int = 0, val timeMinutes: Int? = null)

/**
 * Матрица Эйзенхауэра по уже существующим полям.
 * Срочно — на сегодня, завтра или просрочено (перенесённое); важно — высокий приоритет.
 * Периодические попадают только ближайшим повтором, если он срочный: дальние повторы — не задачи на сейчас.
 */
object Matrix {
    /** Сколько дней вперёд считать «срочным»: сегодня и завтра. */
    const val URGENT_DAYS = 1

    fun isUrgent(item: MatrixItem, today: Long): Boolean = item.day != null && item.day <= today + URGENT_DAYS

    fun isImportant(item: MatrixItem): Boolean = item.priority == Priority.HIGH

    fun quadrant(item: MatrixItem, today: Long): Quadrant = Quadrant.of(isUrgent(item, today), isImportant(item))

    /** Раскладка по квадрантам; внутри — сначала раньше по дню и времени, переносы — выше. */
    fun build(items: List<MatrixItem>, today: Long): Map<Quadrant, List<MatrixItem>> {
        val shown = items.filter { it.type != EntryType.RECURRING || isUrgent(it, today) }
        val grouped = shown.groupBy { quadrant(it, today) }
        return Quadrant.entries.associateWith { q ->
            grouped[q].orEmpty().sortedWith(
                compareBy<MatrixItem>({ it.day ?: Long.MAX_VALUE }, { -it.rollovers }, { it.timeMinutes ?: Int.MAX_VALUE }, { it.id }),
            )
        }
    }
}
