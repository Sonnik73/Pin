package com.sonnik.pin.engine

import java.time.LocalDate

/**
 * Кто человек пользователю. Имена enum хранятся в базе и файлах; кроме них можно записать свою категорию
 * обычным текстом («крёстный», «тренер») — см. [key] и [title].
 */
enum class Relation(val title: String) {
    WIFE("жена"),
    HUSBAND("муж"),
    FAMILY("родственник"),
    FRIEND("друг"),
    COLLEAGUE("коллега"),
    OTHER("знакомый");

    companion object {
        fun parse(value: String?): Relation = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: OTHER

        /** Встроенная категория по имени enum или по русскому названию; null — своя. */
        fun known(value: String?): Relation? {
            val v = value?.trim() ?: return null
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) || it.title.equals(v, ignoreCase = true) }
        }

        /** Что хранить: имя enum для встроенных, текст — для своей категории (с маленькой буквы, без лишних пробелов). */
        fun key(value: String?): String {
            known(value)?.let { return it.name }
            val custom = value?.trim()?.replace(Regex("""\s+"""), " ").orEmpty()
            return if (custom.isEmpty()) OTHER.name else custom.lowercase()
        }

        /** Подпись для экрана: «жена», «муж»… или своя категория как есть. */
        fun title(value: String?): String = known(value)?.title ?: value?.trim()?.takeIf { it.isNotEmpty() } ?: OTHER.title

        /** Свои категории, уже встречающиеся у людей, — чтобы предложить их кнопками. */
        fun customOf(values: Collection<String?>): List<String> =
            values.filter { known(it) == null }.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    }
}

/** Статус подарка: идея → куплен → подарен. */
enum class GiftStatus(val title: String) {
    IDEA("идея"),
    BOUGHT("куплен"),
    GIVEN("подарен");

    companion object {
        fun parse(value: String?): GiftStatus = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: IDEA
    }
}

/** Повод: день рождения или праздник, ближайший его день. */
data class Occasion(
    val entryId: String,
    val type: EntryType,
    val title: String,
    val day: Long,
    val personId: String? = null,
    /** Сколько исполнится в этот день (если известен год рождения). */
    val age: Int? = null,
)

/** Подарок для расчёта напоминаний. */
data class GiftInfo(val id: String, val occasionId: String?, val personId: String?, val status: GiftStatus)

/** Напоминание о поводе: «через 7 дней — день рождения Маши». */
data class OccasionNotice(val occasion: Occasion, val daysLeft: Long, val needGift: Boolean)

object Occasions {
    /** Сколько лет исполнится в день [day] человеку, родившемуся в [birthYear]. */
    fun ageOn(birthYear: Int?, day: Long): Int? {
        val year = birthYear ?: return null
        val age = LocalDate.ofEpochDay(day).year - year
        return age.takeIf { it in 0..150 }
    }

    /** Дни, за сколько напоминать: «7,1» → [7, 1]. */
    fun parseRemindDays(text: String?): List<Int> =
        text?.split(',', ' ', ';')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..60 }?.distinct()?.sortedDescending()
            ?: DEFAULT_REMIND_DAYS

    fun formatRemindDays(days: List<Int>): String = days.distinct().sortedDescending().joinToString(",")

    val DEFAULT_REMIND_DAYS = listOf(7, 1)

    /** За сколько дней до повода напоминать купить подарок. */
    const val GIFT_LEAD_DAYS = 7

    /**
     * Напоминания на сегодня: повод через ровно N дней из его списка напоминаний; праздники — за день.
     * Если до повода неделя или меньше, а купленного (или подаренного) подарка для него нет, но есть идеи
     * или это день рождения — нужен подарок.
     */
    fun notices(
        occasions: List<Occasion>,
        remindDaysOf: (Occasion) -> List<Int>,
        gifts: List<GiftInfo>,
        today: Long,
    ): List<OccasionNotice> = occasions.mapNotNull { o ->
        val left = o.day - today
        if (left <= 0) return@mapNotNull null
        val forOccasion = gifts.filter { it.occasionId == o.entryId }
        val hasGift = forOccasion.any { it.status != GiftStatus.IDEA }
        val wantsGift = forOccasion.isNotEmpty() || o.type == EntryType.BIRTHDAY
        val needGift = left <= GIFT_LEAD_DAYS && wantsGift && !hasGift
        val remind = left.toInt() in remindDaysOf(o)
        // «Купить подарок» — каждый день последней недели, пока не куплен; обычное напоминание — в свои дни.
        if (remind || needGift) OccasionNotice(o, left, needGift) else null
    }.sortedBy { it.daysLeft }

    /** «Через 7 дней — день рождения: Маша, 30 лет». */
    fun describe(notice: OccasionNotice): String {
        val o = notice.occasion
        val whenText = when (notice.daysLeft) {
            1L -> "Завтра"
            2L -> "Послезавтра"
            else -> "Через ${notice.daysLeft} ${RuText.plural(notice.daysLeft, "день", "дня", "дней")}"
        }
        val what = when (o.type) {
            EntryType.BIRTHDAY -> "день рождения: ${o.title}" + (o.age?.let { ", ${it} ${RuText.plural(it, "год", "года", "лет")}" } ?: "")
            else -> o.title
        }
        val gift = if (notice.needGift) " — купить подарок" else ""
        return "$whenText — $what$gift"
    }
}

/** Горизонт плана без точной даты. */
sealed interface PlanScope {
    data object Someday : PlanScope
    data class Month(val year: Int, val month: Int) : PlanScope
    data class Year(val year: Int) : PlanScope

    companion object {
        private val MONTHS = listOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
        )

        /** `someday`, `2026-12`, `2027`. Неизвестное — «когда-нибудь». */
        fun parse(text: String?): PlanScope {
            val t = text?.trim().orEmpty()
            Regex("""(\d{4})-(\d{1,2})""").matchEntire(t)?.let { m ->
                val month = m.groupValues[2].toInt()
                if (month in 1..12) return Month(m.groupValues[1].toInt(), month)
            }
            Regex("""\d{4}""").matchEntire(t)?.let { return Year(it.value.toInt()) }
            return Someday
        }

        fun format(scope: PlanScope): String = when (scope) {
            Someday -> "someday"
            is Month -> "%04d-%02d".format(scope.year, scope.month)
            is Year -> scope.year.toString()
        }

        fun describe(scope: PlanScope): String = when (scope) {
            Someday -> "когда-нибудь"
            is Month -> "${MONTHS[scope.month - 1]} ${scope.year}"
            is Year -> "${scope.year} год"
        }

        /** Порядок в списке: ближние раньше, «когда-нибудь» в конце. */
        fun sortKey(scope: PlanScope): Int = when (scope) {
            is Month -> scope.year * 100 + scope.month
            is Year -> scope.year * 100 + 99
            Someday -> Int.MAX_VALUE
        }

        /** День, который предложить при превращении плана в задачу. */
        fun suggestedDay(scope: PlanScope, today: Long): Long = when (scope) {
            Someday -> today
            is Month -> maxOf(today, LocalDate.of(scope.year, scope.month, 1).toEpochDay())
            is Year -> maxOf(today, LocalDate.of(scope.year, 1, 1).toEpochDay())
        }
    }
}
