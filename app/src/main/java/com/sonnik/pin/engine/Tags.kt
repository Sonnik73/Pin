package com.sonnik.pin.engine

/** Подсказки тегов при вводе «дом, раб…»: дополняется последний, ещё не дописанный тег. */
object Tags {
    private fun tokens(input: String) = input.split(',').map { it.trim().removePrefix("#").lowercase() }

    /** Подходящие теги для последнего слова: сначала начинающиеся с него, потом содержащие; уже введённые — нет. */
    fun suggest(all: Collection<String>, input: String, limit: Int = 6): List<String> {
        val parts = tokens(input)
        val current = parts.last()
        val entered = parts.dropLast(1).toSet()
        val candidates = all.map { it.lowercase() }.distinct().filter { it !in entered && it != current }
        if (current.isEmpty()) return emptyList()
        val starts = candidates.filter { it.startsWith(current) }.sorted()
        val contains = candidates.filter { !it.startsWith(current) && it.contains(current) }.sorted()
        return (starts + contains).take(limit)
    }

    /** Подставить [tag] вместо недописанного последнего слова и оставить место для следующего: «дом, работа, ». */
    fun apply(input: String, tag: String): String {
        val head = input.substringBeforeLast(',', "").trim()
        return (if (head.isEmpty()) "" else "$head, ") + tag + ", "
    }
}
