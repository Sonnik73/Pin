package com.sonnik.pin.engine

/** Вид праздника: выходной (красное число в календаре), памятная дата или военный праздник. */
enum class HolidayKind(val title: String, val emoji: String) {
    DAY_OFF("выходной", "🎉"),
    MEMORABLE("памятная дата", "✨"),
    MILITARY("военный праздник", "🎖"),
}

/** Встроенный праздник: ключ стабилен (по нему запись опознаётся в базе и файлах). */
data class BuiltInHoliday(
    val key: String,
    val title: String,
    val month: Int,
    val day: Int,
    val kind: HolidayKind = HolidayKind.DAY_OFF,
) {
    /** id записи-праздника в базе. */
    val entryId: String get() = "holiday-$key"
    val rule: RepeatRule.Yearly get() = RepeatRule.Yearly(month, day)
}

/**
 * Российские праздники: официальные выходные, популярные памятные даты и выбранные военные.
 * Даты — фиксированные (праздники «в последнее воскресенье» сюда не входят). Любой можно выключить.
 */
object Holidays {
    private val OFF = HolidayKind.DAY_OFF
    private val MEM = HolidayKind.MEMORABLE
    private val MIL = HolidayKind.MILITARY

    val RU: List<BuiltInHoliday> = listOf(
        BuiltInHoliday("new-year", "Новый год", 1, 1, OFF),
        BuiltInHoliday("christmas", "Рождество", 1, 7, OFF),
        BuiltInHoliday("old-new-year", "Старый Новый год", 1, 13, MEM),
        BuiltInHoliday("students", "Татьянин день — День студентов", 1, 25, MEM),
        BuiltInHoliday("valentine", "День святого Валентина", 2, 14, MEM),
        BuiltInHoliday("defender", "День защитника Отечества", 2, 23, OFF),
        BuiltInHoliday("special-ops", "День Сил специальных операций", 2, 27, MIL),
        BuiltInHoliday("womens-day", "Международный женский день", 3, 8, OFF),
        BuiltInHoliday("cosmonautics", "День космонавтики", 4, 12, MEM),
        BuiltInHoliday("may-day", "Праздник весны и труда", 5, 1, OFF),
        BuiltInHoliday("victory", "День Победы", 5, 9, OFF),
        BuiltInHoliday("children", "День защиты детей", 6, 1, MEM),
        BuiltInHoliday("russia-day", "День России", 6, 12, OFF),
        BuiltInHoliday("youth", "День молодёжи", 6, 27, MEM),
        BuiltInHoliday("family", "День семьи, любви и верности", 7, 8, MEM),
        BuiltInHoliday("airborne", "День ВДВ", 8, 2, MIL),
        BuiltInHoliday("aerospace", "День Воздушно-космических сил", 8, 12, MIL),
        BuiltInHoliday("flag", "День Государственного флага", 8, 22, MEM),
        BuiltInHoliday("knowledge", "День знаний", 9, 1, MEM),
        BuiltInHoliday("ground-forces", "День Сухопутных войск", 10, 1, MIL),
        BuiltInHoliday("elderly", "День пожилых людей", 10, 1, MEM),
        BuiltInHoliday("teacher", "День учителя", 10, 5, MEM),
        BuiltInHoliday("signal", "День военного связиста", 10, 20, MIL),
        BuiltInHoliday("unity", "День народного единства", 11, 4, OFF),
        BuiltInHoliday("artillery", "День ракетных войск и артиллерии", 11, 19, MIL),
        BuiltInHoliday("constitution", "День Конституции", 12, 12, MEM),
        BuiltInHoliday("strategic-missile", "День РВСН", 12, 17, MIL),
        BuiltInHoliday("new-years-eve", "Канун Нового года", 12, 31, OFF),
    )

    fun byEntryId(id: String): BuiltInHoliday? = RU.firstOrNull { it.entryId == id }

    /** Вид праздника записи: встроенный — по списку, свой (годовщина и т. п.) — как выходной, красным. */
    fun kindOf(entryId: String): HolidayKind = byEntryId(entryId)?.kind ?: HolidayKind.DAY_OFF
}
