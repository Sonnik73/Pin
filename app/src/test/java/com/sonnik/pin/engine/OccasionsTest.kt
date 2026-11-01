package com.sonnik.pin.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccasionsTest {
    private val today = LocalDate.of(2026, 11, 1).toEpochDay()

    @Test
    fun `возраст и дни напоминаний`() {
        assertEquals(30, Occasions.ageOn(1996, LocalDate.of(2026, 3, 5).toEpochDay()))
        assertNull(Occasions.ageOn(null, today))
        assertEquals(listOf(7, 1), Occasions.parseRemindDays(null))
        assertEquals(listOf(14, 3), Occasions.parseRemindDays("3, 14, 3, 0, x"))
        assertEquals("14,3", Occasions.formatRemindDays(listOf(3, 14)))
    }

    private fun birthday(inDays: Long, age: Int? = 30) =
        Occasion("b", EntryType.BIRTHDAY, "Маша", today + inDays, personId = "p", age = age)

    @Test
    fun `напоминания за 7 и 1 день и подарок`() {
        val remind = { _: Occasion -> listOf(7, 1) }
        val in7 = Occasions.notices(listOf(birthday(7)), remind, emptyList(), today).single()
        assertTrue(in7.needGift)
        assertEquals("Через 7 дней — день рождения: Маша, 30 лет — купить подарок", Occasions.describe(in7))

        // Подарок куплен — напоминание без «купить».
        val bought = listOf(GiftInfo("g", "b", "p", GiftStatus.BOUGHT))
        val in1 = Occasions.notices(listOf(birthday(1, age = 21)), remind, bought, today).single()
        assertFalse(in1.needGift)
        assertEquals("Завтра — день рождения: Маша, 21 год", Occasions.describe(in1))

        // За 5 дней — только если подарка нет.
        assertEquals(1, Occasions.notices(listOf(birthday(5)), remind, emptyList(), today).size)
        assertTrue(Occasions.notices(listOf(birthday(5)), remind, bought, today).isEmpty())
        // За 10 дней — ничего.
        assertTrue(Occasions.notices(listOf(birthday(10)), remind, emptyList(), today).isEmpty())
    }

    @Test
    fun `праздник без идей подарка не требует подарка`() {
        val holiday = Occasion("holiday-womens-day", EntryType.HOLIDAY, "8 марта", today + 3)
        val remind = { _: Occasion -> listOf(1) }
        assertTrue(Occasions.notices(listOf(holiday), remind, emptyList(), today).isEmpty())
        val idea = listOf(GiftInfo("g", "holiday-womens-day", "wife", GiftStatus.IDEA))
        val notice = Occasions.notices(listOf(holiday), remind, idea, today).single()
        assertTrue(notice.needGift)
        assertEquals("Через 3 дня — 8 марта — купить подарок", Occasions.describe(notice))
    }

    @Test
    fun `планы`() {
        assertEquals(PlanScope.Month(2026, 12), PlanScope.parse("2026-12"))
        assertEquals(PlanScope.Year(2027), PlanScope.parse("2027"))
        assertEquals(PlanScope.Someday, PlanScope.parse("когда-нибудь"))
        assertEquals("2026-03", PlanScope.format(PlanScope.Month(2026, 3)))
        assertEquals("декабрь 2026", PlanScope.describe(PlanScope.Month(2026, 12)))
        assertTrue(PlanScope.sortKey(PlanScope.Month(2026, 12)) < PlanScope.sortKey(PlanScope.Year(2026)))
        assertTrue(PlanScope.sortKey(PlanScope.Year(2027)) < PlanScope.sortKey(PlanScope.Someday))
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), PlanScope.suggestedDay(PlanScope.Month(2026, 12), today))
        assertEquals(today, PlanScope.suggestedDay(PlanScope.Month(2026, 1), today))
    }

    @Test
    fun `день рождения не переносится, а закрывается до следующего года`() {
        val day = LocalDate.of(2026, 3, 5).toEpochDay()
        val schedule = Schedule(RepeatRule.Yearly(3, 5), day, rollsOver = false)
        val task = TaskSnapshot("b", EntryType.BIRTHDAY, TaskStatus.OPEN, day, day)
        val result = Rollover.apply(task, day + 1, schedule)!!
        assertEquals(LocalDate.of(2027, 3, 5).toEpochDay(), result.task.currentDay)
        assertEquals(listOf(MissedEvent("b", day, day + 1, 0)), result.missed)
        assertTrue(result.events.isEmpty())
        // Через два года без открытия — оба пропущены.
        val far = Rollover.apply(task, LocalDate.of(2027, 6, 1).toEpochDay(), schedule)!!
        assertEquals(2, far.missed.size)
        assertEquals(LocalDate.of(2028, 3, 5).toEpochDay(), far.task.currentDay)
    }

    @Test
    fun `встроенные праздники`() {
        assertEquals(9, Holidays.RU.size)
        assertEquals("holiday-unity", Holidays.RU.first { it.month == 11 }.entryId)
        assertEquals(Holidays.RU[0], Holidays.byEntryId("holiday-new-year"))
    }

    @Test
    fun `ближайший день рождения с возрастом`() {
        val nov1 = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
        assertEquals("29 ноября, через 28 дней · исполнится\u00A051", Occasions.nextBirthdayText(11, 29, 1975, nov1))
        assertEquals("1 ноября, сегодня! · исполнится\u00A030", Occasions.nextBirthdayText(11, 1, 1996, nov1))
        // Уже прошёл в этом году — следующий, и возраст на него.
        assertEquals("4 января, через 64 дня · исполнится\u00A030", Occasions.nextBirthdayText(1, 4, 1997, nov1))
        assertEquals("2 ноября, завтра", Occasions.nextBirthdayText(11, 2, null, nov1))
    }
}
