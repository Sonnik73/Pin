package com.sonnik.pin.engine

/** Цвета списков: светлый вариант для светлой темы и мягкий — для тёмной. Хранится индекс-имя. */
enum class ListColor(val light: Long, val dark: Long, val title: String) {
    RED(0xFFD9483E, 0xFFFF8A80, "красный"),
    ORANGE(0xFFE8710A, 0xFFFFB74D, "оранжевый"),
    AMBER(0xFFB7791F, 0xFFF0B45A, "янтарный"),
    GREEN(0xFF2E7D32, 0xFF81C784, "зелёный"),
    TEAL(0xFF00796B, 0xFF4DB6AC, "бирюзовый"),
    BLUE(0xFF1E63B5, 0xFF90CAF9, "синий"),
    VIOLET(0xFF6A4BA8, 0xFFCDB8FF, "фиолетовый"),
    GRAY(0xFF5F6672, 0xFFB0B6C0, "серый");

    companion object {
        fun parse(value: String?): ListColor = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: BLUE

        /** Цвет для нового списка: первый не занятый. */
        fun next(used: Collection<String>): ListColor = entries.firstOrNull { c -> used.none { it.equals(c.name, ignoreCase = true) } } ?: BLUE
    }
}

/** Список (проект) для сопоставления по имени. */
data class TaskListRef(val id: String, val name: String)

object Lists {
    /** Список по имени из «@дом»: точное совпадение без регистра, иначе — единственный, начинающийся так же. */
    fun match(lists: List<TaskListRef>, name: String?): TaskListRef? {
        val n = name?.let(Search::normalize)?.takeIf { it.isNotEmpty() } ?: return null
        lists.firstOrNull { Search.normalize(it.name) == n }?.let { return it }
        return lists.filter { Search.normalize(it.name).startsWith(n) }.singleOrNull()
    }
}
