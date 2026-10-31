package com.sonnik.pin.engine

/** Встроенный праздник: ключ стабилен (по нему запись опознаётся в базе и файлах). */
data class BuiltInHoliday(val key: String, val title: String, val month: Int, val day: Int) {
    /** id записи-праздника в базе. */
    val entryId: String get() = "holiday-$key"
    val rule: RepeatRule.Yearly get() = RepeatRule.Yearly(month, day)
}

/** Российские праздники из спецификации. */
object Holidays {
    val RU: List<BuiltInHoliday> = listOf(
        BuiltInHoliday("new-year", "Новый год", 1, 1),
        BuiltInHoliday("christmas", "Рождество", 1, 7),
        BuiltInHoliday("defender", "День защитника Отечества", 2, 23),
        BuiltInHoliday("womens-day", "Международный женский день", 3, 8),
        BuiltInHoliday("may-day", "Праздник весны и труда", 5, 1),
        BuiltInHoliday("victory", "День Победы", 5, 9),
        BuiltInHoliday("russia-day", "День России", 6, 12),
        BuiltInHoliday("unity", "День народного единства", 11, 4),
        BuiltInHoliday("new-years-eve", "Канун Нового года", 12, 31),
    )

    fun byEntryId(id: String): BuiltInHoliday? = RU.firstOrNull { it.entryId == id }
}
