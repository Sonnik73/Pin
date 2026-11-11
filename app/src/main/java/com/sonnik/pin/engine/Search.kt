package com.sonnik.pin.engine

/** Запись для поиска — без Room. [extra] — дополнительный текст для поиска (имя человека, повод). */
data class SearchItem(
    val id: String,
    val type: EntryType,
    val title: String,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val priority: Priority = Priority.NORMAL,
    val status: TaskStatus = TaskStatus.OPEN,
    val dueDay: Long? = null,
    val extra: String = "",
    /** Когда выполнено (логический день), для порядка в архиве; null — не выполнено или неизвестно. */
    val doneDay: Long? = null,
    /** Время последней правки — чтобы в один день выше было сделанное позже. */
    val updatedAt: Long = 0,
)

/** Состояние для фильтра «Все записи». */
enum class SearchState(val title: String) {
    ALL("Все"),
    OPEN("Открытые"),
    OVERDUE("Просрочено"),
    NO_DATE("Без даты"),
    DONE("Выполнено"),
}

data class SearchFilter(
    val query: String = "",
    val types: Set<EntryType> = emptySet(),
    val tag: String? = null,
    val importantOnly: Boolean = false,
    val state: SearchState = SearchState.ALL,
) {
    val isEmpty: Boolean get() = query.isBlank() && types.isEmpty() && tag == null && !importantOnly && state == SearchState.ALL
}

object Search {
    /** Без регистра, «ё» = «е», без лишних пробелов. */
    /** Подходит ли запрос к тексту: каждое слово запроса — начало или часть какого-то поля (без регистра, ё = е). */
    fun matchesAll(query: String, fields: List<String?>): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = normalize(fields.filterNotNull().joinToString(" "))
        return words.all { it in haystack }
    }

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("""\s+"""), " ").trim()

    /**
     * Отбор и порядок. Слова запроса должны найтись все (в названии, описании, тегах или [SearchItem.extra]);
     * выше — совпадения в названии, затем открытые с ближней датой, затем выполненные — сначала новые.
     * Просрочено — открытая задача, которая уже переезжала (её исходный день прошёл) или стоит раньше сегодня.
     */
    fun filter(items: List<SearchItem>, f: SearchFilter, today: Long, rolloversOf: (String) -> Int = { 0 }): List<SearchItem> {
        val words = normalize(f.query).split(' ').filter { it.isNotEmpty() }
        val tag = f.tag?.let(::normalize)
        return items.asSequence()
            .filter { f.types.isEmpty() || it.type in f.types }
            .filter { tag == null || it.tags.any { t -> normalize(t) == tag } }
            .filter { !f.importantOnly || it.priority == Priority.HIGH }
            .filter { item ->
                when (f.state) {
                    SearchState.ALL -> true
                    SearchState.OPEN -> item.status == TaskStatus.OPEN && item.type != EntryType.MOMENT
                    SearchState.DONE -> item.status == TaskStatus.DONE && item.type != EntryType.MOMENT
                    SearchState.NO_DATE -> item.dueDay == null
                    SearchState.OVERDUE -> item.status == TaskStatus.OPEN && item.dueDay != null &&
                        item.type != EntryType.HOLIDAY && item.type != EntryType.MOMENT && (item.dueDay < today || rolloversOf(item.id) > 0)
                }
            }
            .map { item ->
                val title = normalize(item.title)
                val rest = normalize(item.body + " " + item.tags.joinToString(" ") + " " + item.extra)
                val inTitle = words.count { it in title }
                val inRest = words.count { it in rest && it !in title }
                Triple(item, inTitle, inTitle + inRest)
            }
            .filter { (_, _, found) -> found == words.size }
            .sortedWith(
                compareByDescending<Triple<SearchItem, Int, Int>> { it.second }
                    .thenBy { if (it.first.status == TaskStatus.OPEN) 0 else 1 }
                    // Открытые — ближние даты выше; выполненные (архив) — сверху сделанные последними.
                    .thenBy { if (it.first.status == TaskStatus.OPEN) it.first.dueDay ?: Long.MAX_VALUE else -(it.first.doneDay ?: it.first.dueDay ?: Long.MIN_VALUE + 1) }
                    .thenBy { if (it.first.status == TaskStatus.OPEN) 0L else -it.first.updatedAt }
                    .thenBy { normalize(it.first.title) },
            )
            .map { it.first }
            .toList()
    }

    fun typeTitle(type: EntryType): String = when (type) {
        EntryType.ONE_TIME -> "Задачи"
        EntryType.RECURRING -> "Повторы"
        EntryType.BIRTHDAY -> "Дни рождения"
        EntryType.HOLIDAY -> "Праздники"
        EntryType.PLAN -> "Планы"
        EntryType.GIFT -> "Подарки"
        EntryType.HABIT -> "Привычки"
        EntryType.MOMENT -> "Важные даты"
    }
}
