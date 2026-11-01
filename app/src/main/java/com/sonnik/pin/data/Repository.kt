package com.sonnik.pin.data

import androidx.room.withTransaction
import com.sonnik.pin.engine.Completion
import com.sonnik.pin.engine.DayStat
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.StatEvent
import com.sonnik.pin.engine.Stats
import com.sonnik.pin.engine.TodayBoard
import com.sonnik.pin.engine.GiftInfo
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.HabitInfo
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.Holidays
import com.sonnik.pin.engine.JournalTable
import com.sonnik.pin.engine.MdDoc
import com.sonnik.pin.engine.Occasion
import com.sonnik.pin.engine.OccasionNotice
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.LogicalDay
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.Rollover
import com.sonnik.pin.engine.TaskStatus
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Поля, которые пользователь задаёт в редакторе. */
data class TaskDraft(
    val title: String,
    val body: String = "",
    /** Для одноразовой — её день; для периодической — с какого дня начинать повторы. */
    val day: Long,
    val timeMinutes: Int? = null,
    val priority: Priority = Priority.NORMAL,
    val tags: List<String> = emptyList(),
    val repeat: RepeatRule? = null,
    /** Напомнить заранее, минут. */
    val remindBefore: Int = 0,
)

class PinRepository(
    private val db: PinDatabase,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    private val entries = db.entries()
    private val journal = db.journal()
    private val settingsDao = db.settings()
    private val peopleDao = db.people()

    /** Вызывается после любого изменения данных — переставить уведомления, обновить виджет. */
    var onChanged: (() -> Unit)? = null

    val datedEntries: Flow<List<EntryEntity>> = entries.observeDated()

    val allEntries: Flow<List<EntryEntity>> = entries.observeAll()

    val people: Flow<List<PersonEntity>> = peopleDao.observe()

    val journalAll: Flow<List<JournalEntity>> = journal.observeAll()

    /** Отметки привычек: id → дни. */
    val habitDone: Flow<Map<String, Set<Long>>> = journal.observeHabitDone().map { list ->
        list.groupBy({ it.entryId }, { it.day }).mapValues { it.value.toSet() }
    }

    val settings: Flow<SettingsEntity> = settingsDao.observe().map { it ?: SettingsEntity() }

    fun doneBetween(from: Long, to: Long): Flow<List<JournalEntity>> = journal.observeDone(from, to)

    suspend fun settings(): SettingsEntity = settingsDao.get() ?: SettingsEntity()

    suspend fun updateSettings(change: (SettingsEntity) -> SettingsEntity) {
        settingsDao.put(change(settings()))
        onChanged?.invoke()
    }

    /** Служебные отметки (когда что показано) — без [onChanged], чтобы не зациклить уведомления. */
    suspend fun updateSettingsQuietly(change: (SettingsEntity) -> SettingsEntity) {
        settingsDao.put(change(settings()))
    }

    suspend fun logicalDay(): LogicalDay = LogicalDay(clock(), settings().dayBoundaryMinutes)

    fun now(): Long = clock().millis()

    private suspend fun touch(at: Long = now()) {
        settingsDao.put(settings().copy(lastChangedAt = at))
    }

    /** Открытые записи с датой — для напоминаний. */
    suspend fun openDated(): List<EntryEntity> = entries.openDated()

    /**
     * Досчитывает перенос невыполненных задач до сегодняшнего дня. Безопасно вызывать сколько угодно раз.
     * @return сколько задач перенесено.
     */
    suspend fun rollover(): Int {
        val moved = db.withTransaction {
            val day = logicalDay()
            val today = day.today()
            val stale = entries.openBefore(today)
            val byId = stale.associateBy { it.id }
            val results = Rollover.applyAll(stale.mapNotNull { it.toSnapshot() }, today) { byId[it.id]?.schedule }
            val at = now()
            if (results.isNotEmpty()) {
                entries.upsertAll(
                    results.map { r ->
                        byId.getValue(r.task.id).copy(
                            dueDay = r.task.currentDay,
                            originalDay = r.task.originalDay,
                            rollovers = r.task.rollovers,
                            updatedAt = at,
                        )
                    },
                )
                val events = ArrayList<JournalEntity>()
                for (r in results) {
                    val entry = byId.getValue(r.task.id)
                    // Праздник — событие, а не задача: в журнал и статистику не попадает.
                    if (entry.entryType == EntryType.HOLIDAY) continue
                    // Счётчик переносов для каждой ночи: досчитываем от сохранённого, обнуляя на пропущенных повторах.
                    var counter = entry.rollovers
                    var occurrence = entry.originalDay ?: entry.dueDay
                    val timeline = (r.events.map { it.toDay to null } + r.missed.map { it.closedOn to it }).sortedBy { it.first }
                    for ((toDay, missed) in timeline) {
                        if (missed != null) {
                            events += JournalEntity(
                                entryId = entry.id,
                                event = JournalEvent.MISSED,
                                day = missed.occurrenceDay,
                                at = day.startOf(missed.closedOn),
                                kind = entry.type,
                                priority = entry.priority,
                                rollovers = missed.rollovers,
                                occurrenceDay = missed.occurrenceDay,
                            )
                            counter = 0
                            occurrence = missed.closedOn
                        } else {
                            counter++
                            events += JournalEntity(
                                entryId = entry.id,
                                event = JournalEvent.ROLLED,
                                day = toDay - 1,
                                // Время события — граница дня, когда перенос «случился», а не момент досчёта.
                                at = day.startOf(toDay),
                                kind = entry.type,
                                priority = entry.priority,
                                rollovers = counter,
                                occurrenceDay = occurrence,
                            )
                        }
                    }
                }
                journal.insertAll(events)
            }
            val s = settings()
            settingsDao.put(s.copy(lastRolloverDay = today, lastChangedAt = if (results.isEmpty()) s.lastChangedAt else at))
            results.size
        }
        if (moved > 0) onChanged?.invoke()
        return moved
    }

    suspend fun get(id: String): EntryEntity? = entries.get(id)

    /** Первый повтор правила начиная с [day] включительно. */
    private fun firstOccurrence(rule: RepeatRule, day: Long): Long = rule.nextAfter(day - 1, day)

    suspend fun create(draft: TaskDraft): String {
        val at = now()
        val id = UUID.randomUUID().toString()
        val rule = draft.repeat
        val first = rule?.let { firstOccurrence(it, draft.day) } ?: draft.day
        db.withTransaction {
            entries.upsert(
                EntryEntity(
                    id = id,
                    type = (if (rule != null) EntryType.RECURRING else EntryType.ONE_TIME).name,
                    title = draft.title.trim(),
                    body = draft.body,
                    dueDay = first,
                    originalDay = first,
                    timeMinutes = draft.timeMinutes,
                    priority = draft.priority.level,
                    tags = normalizeTags(draft.tags),
                    createdAt = at,
                    updatedAt = at,
                    repeatRule = rule?.let { RepeatRule.format(it) },
                    repeatAnchor = rule?.let { draft.day },
                    remindBefore = draft.remindBefore,
                ),
            )
            touch(at)
        }
        onChanged?.invoke()
        return id
    }

    /**
     * Правка. Если пользователь сам сменил дату или правило повтора — это новый план: исходный день
     * и счётчик переносов сбрасываются.
     */
    suspend fun update(id: String, draft: TaskDraft) {
        db.withTransaction {
            val old = entries.get(id) ?: return@withTransaction
            val at = now()
            val rule = draft.repeat
            val ruleText = rule?.let { RepeatRule.format(it) }
            val oldStart = if (old.repeatRule != null) old.repeatAnchor ?: old.dueDay else old.dueDay
            val replanned = oldStart != draft.day || ruleText != old.repeatRule
            val first = rule?.let { firstOccurrence(it, draft.day) } ?: draft.day
            entries.upsert(
                old.copy(
                    type = when {
                        rule != null -> EntryType.RECURRING.name
                        old.entryType == EntryType.RECURRING -> EntryType.ONE_TIME.name
                        else -> old.type
                    },
                    title = draft.title.trim(),
                    body = draft.body,
                    dueDay = if (replanned) first else old.dueDay,
                    originalDay = if (replanned) first else old.originalDay,
                    rollovers = if (replanned) 0 else old.rollovers,
                    timeMinutes = draft.timeMinutes,
                    priority = draft.priority.level,
                    tags = normalizeTags(draft.tags),
                    repeatRule = ruleText,
                    repeatAnchor = rule?.let { draft.day },
                    remindBefore = draft.remindBefore,
                    snoozeUntil = null,
                    updatedAt = at,
                ),
            )
            touch(at)
        }
        rollover()
        onChanged?.invoke()
    }

    suspend fun delete(id: String) {
        db.withTransaction {
            val old = entries.get(id) ?: return@withTransaction
            val at = now()
            entries.upsert(old.copy(deleted = true, updatedAt = at))
            touch(at)
        }
        onChanged?.invoke()
    }

    /**
     * Отметить выполнение или снять отметку. У периодической задачи выполнение закрывает текущий повтор
     * и ставит следующий; снятие отметки возвращает выполненный повтор.
     */
    suspend fun setDone(id: String, done: Boolean) {
        val changed = db.withTransaction {
            val old = entries.get(id) ?: return@withTransaction false
            val at = now()
            val day = logicalDay().dayOf(at)
            val schedule = old.schedule
            when {
                done && old.entryStatus == TaskStatus.OPEN -> {
                    val snapshot = old.toSnapshot() ?: return@withTransaction false
                    journal.insert(
                        JournalEntity(
                            entryId = id,
                            event = JournalEvent.DONE,
                            day = day,
                            at = at,
                            kind = old.type,
                            priority = old.priority,
                            rollovers = old.rollovers,
                            onTime = Completion.isOnTime(snapshot, day),
                            occurrenceDay = snapshot.originalDay,
                        ),
                    )
                    if (schedule != null) {
                        val next = Rollover.nextOccurrence(snapshot, day, schedule)
                        entries.upsert(
                            old.copy(
                                dueDay = next.currentDay,
                                originalDay = next.originalDay,
                                rollovers = 0,
                                completedAt = at,
                                completedDay = day,
                                updatedAt = at,
                            ),
                        )
                    } else {
                        entries.upsert(old.copy(status = TaskStatus.DONE.name, completedAt = at, completedDay = day, updatedAt = at))
                    }
                }
                !done && schedule != null && old.completedDay != null -> {
                    val last = journal.lastDone(id) ?: return@withTransaction false
                    journal.deleteById(last.id)
                    val occurrence = last.occurrenceDay ?: last.day
                    entries.upsert(
                        old.copy(
                            originalDay = occurrence,
                            dueDay = occurrence + last.rollovers,
                            rollovers = last.rollovers,
                            completedAt = null,
                            completedDay = null,
                            updatedAt = at,
                        ),
                    )
                }
                !done && old.entryStatus == TaskStatus.DONE -> {
                    journal.lastDone(id)?.let { journal.deleteById(it.id) }
                    // Если день выполнения уже прошёл, задача вернётся на сегодня при следующем переносе.
                    entries.upsert(old.copy(status = TaskStatus.OPEN.name, completedAt = null, completedDay = null, updatedAt = at))
                }
                else -> return@withTransaction false
            }
            touch(at)
            true
        }
        if (changed) {
            rollover()
            onChanged?.invoke()
        }
    }

    suspend fun setDayBoundary(minutes: Int) =
        updateSettings { it.copy(dayBoundaryMinutes = minutes.coerceIn(0, LogicalDay.MINUTES_PER_DAY - 1)) }

    // ---------- Люди, дни рождения, праздники, подарки, планы (0.3) ----------

    /** Встроенные праздники появляются в базе один раз; потом их можно только выключить. */
    suspend fun seedHolidays() {
        val today = logicalDay().today()
        val at = now()
        var added = false
        db.withTransaction {
            for (h in Holidays.RU) {
                if (entries.get(h.entryId) != null) continue
                val first = h.rule.nextAfter(today - 1, today)
                entries.upsert(
                    EntryEntity(
                        id = h.entryId,
                        type = EntryType.HOLIDAY.name,
                        title = h.title,
                        dueDay = first,
                        originalDay = first,
                        repeatRule = RepeatRule.format(h.rule),
                        repeatAnchor = first,
                        remindDays = "1",
                        createdAt = at,
                        updatedAt = at,
                    ),
                )
                added = true
            }
        }
        if (added) onChanged?.invoke()
    }

    /** Свой праздник (годовщина свадьбы и т. п.); [id] = null — новый. */
    suspend fun saveHoliday(id: String?, title: String, month: Int, day: Int, remindDays: List<Int>) {
        val rule = RepeatRule.Yearly(month, day)
        val today = logicalDay().today()
        val first = rule.nextAfter(today - 1, today)
        val at = now()
        val old = id?.let { entries.get(it) }
        entries.upsert(
            (old ?: EntryEntity(id = UUID.randomUUID().toString(), type = EntryType.HOLIDAY.name, title = "", createdAt = at, updatedAt = at))
                .copy(
                    title = title.trim(),
                    dueDay = first,
                    originalDay = first,
                    repeatRule = RepeatRule.format(rule),
                    repeatAnchor = first,
                    remindDays = Occasions.formatRemindDays(remindDays),
                    updatedAt = at,
                ),
        )
        touch(at)
        onChanged?.invoke()
    }

    suspend fun setDisabled(id: String, disabled: Boolean) {
        val old = entries.get(id) ?: return
        val at = now()
        entries.upsert(old.copy(disabled = disabled, updatedAt = at))
        touch(at)
        onChanged?.invoke()
    }

    /** День рождения: месяц, число и (необязательно) год. */
    data class Birthday(val month: Int, val day: Int, val year: Int?)

    /** Сохранить человека и его день рождения (null — без дня рождения). Возвращает id человека. */
    /** [relation] — имя встроенной категории ([Relation]) или своя категория текстом. */
    suspend fun savePerson(id: String?, name: String, relation: String, note: String, birthday: Birthday?, remindDays: List<Int>): String {
        val at = now()
        val personId = id ?: UUID.randomUUID().toString()
        val today = logicalDay().today()
        db.withTransaction {
            val old = peopleDao.get(personId)
            peopleDao.upsert(
                (old ?: PersonEntity(personId, name, Relation.key(relation), createdAt = at, updatedAt = at))
                    .copy(name = name.trim(), relation = Relation.key(relation), note = note, updatedAt = at, deleted = false),
            )
            val existing = entries.all().firstOrNull { it.entryType == EntryType.BIRTHDAY && it.personId == personId }
            if (birthday == null) {
                existing?.let { entries.upsert(it.copy(deleted = true, updatedAt = at)) }
            } else {
                val rule = RepeatRule.Yearly(birthday.month, birthday.day)
                val ruleText = RepeatRule.format(rule)
                val sameDate = existing != null && !existing.deleted && existing.repeatRule == ruleText
                val first = rule.nextAfter(today - 1, today)
                val base = existing ?: EntryEntity(
                    id = UUID.randomUUID().toString(),
                    type = EntryType.BIRTHDAY.name,
                    title = "",
                    personId = personId,
                    createdAt = at,
                    updatedAt = at,
                )
                entries.upsert(
                    base.copy(
                        title = "Поздравить: ${name.trim()}",
                        dueDay = if (sameDate) base.dueDay else first,
                        originalDay = if (sameDate) base.originalDay else first,
                        repeatRule = ruleText,
                        repeatAnchor = if (sameDate) base.repeatAnchor else first,
                        birthYear = birthday.year,
                        remindDays = Occasions.formatRemindDays(remindDays),
                        priority = Priority.HIGH.level,
                        deleted = false,
                        updatedAt = at,
                    ),
                )
            }
            touch(at)
        }
        onChanged?.invoke()
        return personId
    }

    /** Удалить человека вместе с его днём рождения и подарками (пометкой). */
    suspend fun deletePerson(id: String) {
        val at = now()
        db.withTransaction {
            val person = peopleDao.get(id) ?: return@withTransaction
            peopleDao.upsert(person.copy(deleted = true, updatedAt = at))
            entries.all().filter { it.personId == id }.forEach { entries.upsert(it.copy(deleted = true, updatedAt = at)) }
            touch(at)
        }
        onChanged?.invoke()
    }

    data class GiftDraft(
        val title: String,
        val personId: String?,
        val occasionId: String?,
        val price: String,
        val link: String,
        val status: GiftStatus,
        val body: String = "",
    )

    suspend fun saveGift(id: String?, draft: GiftDraft): String {
        val at = now()
        val giftId = id ?: UUID.randomUUID().toString()
        val old = entries.get(giftId)
        entries.upsert(
            (old ?: EntryEntity(id = giftId, type = EntryType.GIFT.name, title = "", createdAt = at, updatedAt = at)).copy(
                title = draft.title.trim(),
                personId = draft.personId,
                occasionId = draft.occasionId,
                price = draft.price.trim().ifEmpty { null },
                link = draft.link.trim().ifEmpty { null },
                giftStatus = draft.status.name,
                body = draft.body,
                updatedAt = at,
            ),
        )
        touch(at)
        onChanged?.invoke()
        return giftId
    }

    suspend fun setGiftStatus(id: String, status: GiftStatus) {
        val old = entries.get(id) ?: return
        val at = now()
        entries.upsert(old.copy(giftStatus = status.name, updatedAt = at))
        touch(at)
        onChanged?.invoke()
    }

    suspend fun savePlan(id: String?, title: String, body: String, scope: PlanScope, tags: List<String>): String {
        val at = now()
        val planId = id ?: UUID.randomUUID().toString()
        val old = entries.get(planId)
        entries.upsert(
            (old ?: EntryEntity(id = planId, type = EntryType.PLAN.name, title = "", createdAt = at, updatedAt = at)).copy(
                title = title.trim(),
                body = body,
                planScope = PlanScope.format(scope),
                tags = normalizeTags(tags),
                updatedAt = at,
            ),
        )
        touch(at)
        onChanged?.invoke()
        return planId
    }

    /** План становится одноразовой задачей на [day] (запись та же — история и id сохраняются). */
    suspend fun planToTask(id: String, day: Long) {
        val old = entries.get(id) ?: return
        val at = now()
        entries.upsert(
            old.copy(type = EntryType.ONE_TIME.name, dueDay = day, originalDay = day, planScope = null, rollovers = 0, updatedAt = at),
        )
        touch(at)
        rollover()
        onChanged?.invoke()
    }

    /** Отложить напоминание до [until] (кнопки «+15 мин», «+1 ч» в уведомлении). */
    suspend fun snooze(id: String, until: Long) {
        val old = entries.get(id) ?: return
        entries.upsert(old.copy(snoozeUntil = until))
        onChanged?.invoke()
    }

    // ---------- Привычки (2.4) ----------

    /** Создать или изменить привычку; [rule] = null — каждый день. Ведётся с сегодняшнего дня. */
    suspend fun saveHabit(id: String?, title: String, rule: RepeatRule?): String {
        val at = now()
        val habitId = id ?: UUID.randomUUID().toString()
        val old = entries.get(habitId)
        entries.upsert(
            (old ?: EntryEntity(
                id = habitId,
                type = EntryType.HABIT.name,
                title = "",
                repeatAnchor = logicalDay().today(),
                createdAt = at,
                updatedAt = at,
            )).copy(title = title.trim(), repeatRule = rule?.let { RepeatRule.format(it) }, updatedAt = at),
        )
        touch(at)
        onChanged?.invoke()
        return habitId
    }

    /** Отметить привычку за [day] или снять отметку. Будущие дни не отмечаются. */
    suspend fun toggleHabit(id: String, day: Long) {
        val today = logicalDay().today()
        if (day > today) return
        val habit = entries.get(id) ?: return
        val at = now()
        db.withTransaction {
            val existing = journal.habitDoneOn(id, day)
            if (existing != null) {
                journal.deleteById(existing.id)
            } else {
                journal.insert(
                    JournalEntity(
                        entryId = id,
                        event = JournalEvent.DONE,
                        day = day,
                        at = at,
                        kind = EntryType.HABIT.name,
                        priority = habit.priority,
                        rollovers = 0,
                        onTime = day == today,
                        occurrenceDay = day,
                    ),
                )
            }
            touch(at)
        }
        onChanged?.invoke()
    }

    // ---------- Фокус (2.5) ----------

    /** Записать минуты фокуса: событие журнала `FOCUS`, минуты — в поле rollovers (см. docs/FORMAT.md). */
    suspend fun logFocus(taskId: String?, minutes: Int, startedAt: Long) {
        val day = logicalDay().dayOf(startedAt)
        val task = taskId?.let { entries.get(it) }
        journal.insert(
            JournalEntity(
                entryId = taskId ?: FOCUS_NO_TASK,
                event = JournalEvent.FOCUS,
                day = day,
                at = startedAt,
                kind = task?.type ?: JournalEvent.FOCUS,
                priority = task?.priority ?: Priority.NORMAL.level,
                rollovers = minutes,
            ),
        )
        touch(now())
        onChanged?.invoke()
    }

    /** Минуты и отрезки фокуса по дням — для Крепости. */
    suspend fun focusDays(from: Long, to: Long): List<Triple<Long, Int, Int>> {
        val byDay = journal.between(from, to).filter { it.event == JournalEvent.FOCUS }.groupBy { it.day }
        return (from..to).map { d -> byDay[d].orEmpty().let { Triple(d, it.sumOf { e -> e.rollovers }, it.size) } }
    }

    // ---------- «Разбор дня» (2.2) ----------

    /**
     * Задача остаётся на [day] осознанно: новый план — исходный день = [day], счётчик переносов обнуляется
     * (история переносов остаётся в журнале). У периодической день повтора не меняется.
     */
    suspend fun reschedule(id: String, day: Long) {
        val old = entries.get(id) ?: return
        val at = now()
        val recurring = old.schedule != null
        entries.upsert(
            old.copy(
                dueDay = day,
                originalDay = if (recurring) old.originalDay else day,
                rollovers = 0,
                updatedAt = at,
            ),
        )
        touch(at)
        rollover()
        onChanged?.invoke()
    }

    /** Задача уходит в планы «когда-нибудь». */
    suspend fun moveToPlans(id: String) {
        val old = entries.get(id) ?: return
        val at = now()
        entries.upsert(
            old.copy(
                type = EntryType.PLAN.name,
                dueDay = null,
                originalDay = null,
                timeMinutes = null,
                rollovers = 0,
                repeatRule = null,
                repeatAnchor = null,
                planScope = PlanScope.format(PlanScope.Someday),
                updatedAt = at,
            ),
        )
        touch(at)
        onChanged?.invoke()
    }

    /** Пропустить текущий повтор периодической задачи: в журнал — пропуск, задача — на следующий повтор. */
    suspend fun skipOccurrence(id: String) {
        db.withTransaction {
            val old = entries.get(id) ?: return@withTransaction
            val schedule = old.schedule ?: return@withTransaction
            val snapshot = old.toSnapshot() ?: return@withTransaction
            val day = logicalDay()
            val today = day.today()
            val at = now()
            val (next, missed) = Rollover.skipOccurrence(snapshot, today, schedule)
            journal.insert(
                JournalEntity(
                    entryId = id,
                    event = JournalEvent.MISSED,
                    day = missed.occurrenceDay,
                    at = at,
                    kind = old.type,
                    priority = old.priority,
                    rollovers = missed.rollovers,
                    occurrenceDay = missed.occurrenceDay,
                ),
            )
            entries.upsert(old.copy(dueDay = next.currentDay, originalDay = next.originalDay, rollovers = 0, updatedAt = at))
            touch(at)
        }
        onChanged?.invoke()
    }

    /** Поводы (дни рождения и включённые праздники) и подарки — для напоминаний. */
    suspend fun occasionNotices(today: Long): Pair<List<String>, List<OccasionNotice>> {
        val all = entries.all()
        return occasionNotices(all, peopleDao.allWithDeleted(), today)
    }

    // ---------- Данные для Крепости (0.7) ----------

    /** Сводки по дням для провайдера: те же цифры, что в «Итогах». */
    suspend fun dayStats(from: Long, to: Long): List<DayStat> {
        val today = logicalDay().today()
        val events = journal.between(from, to).map { it.toStatEvent() }
        val openToday = if (today in from..to) {
            TodayBoard.build(entries.openDated().filter { !it.disabled }.mapNotNull { it.toSnapshot() }, today, { null }).openCount
        } else {
            0
        }
        return Stats.days(events, from, to, mapOf(today to openToday))
    }

    /** Привычки по дням для Крепости: сколько отмечено и сколько было запланировано. */
    suspend fun habitDays(from: Long, to: Long): List<Triple<Long, Int, Int>> {
        val done = journal.habitDoneBetween(from, to).groupBy({ it.entryId }, { it.day }).mapValues { it.value.toSet() }
        val today = logicalDay().today()
        val habits = entries.all().filter { it.entryType == EntryType.HABIT }
            .map { HabitInfo(it.id, it.rule, it.repeatAnchor ?: today, done[it.id].orEmpty()) }
        return (from..to).map { d -> Habits.dayCounts(habits, d).let { (n, total) -> Triple(d, n, total) } }
    }

    suspend fun completionsSince(since: Long): List<JournalEntity> =
        journal.doneSince(since).filter { EntryType.parse(it.kind) != EntryType.HOLIDAY }

    // ---------- Файлы .md: экспорт, импорт (0.4), синхронизация (0.6) ----------

    data class ImportReport(val added: Int, val updated: Int, val unchanged: Int, val journal: Int, val skipped: List<String>) {
        fun describe(): String = buildString {
            append("Новых записей: $added, обновлено: $updated, без изменений: $unchanged")
            if (journal > 0) append(", событий журнала: $journal")
            if (skipped.isNotEmpty()) append(". Пропущено файлов: ${skipped.size} (${skipped.take(3).joinToString()})")
        }
    }

    /** Когда менялась запись или человек по ключу файла (`e:<id>`, `p:<id>`). */
    suspend fun updatedAtOf(key: String): Long? = when {
        key.startsWith("e:") -> entries.get(key.removePrefix("e:"))?.updatedAt
        key.startsWith("p:") -> peopleDao.get(key.removePrefix("p:"))?.updatedAt
        else -> null
    }

    suspend fun exportFiles(includeDeleted: Boolean = false): List<MdFiles.File> {
        val zone = clock().zone
        return MdFiles.export(entries.allWithDeleted(), peopleDao.allWithDeleted(), journal.all(), zone, includeDeleted)
    }

    /**
     * Слияние файлов с базой: запись побеждает, если её `updated` новее; события журнала добавляются без дублей.
     * Люди читаются первыми, чтобы ссылки `person: [[Имя]]` нашлись.
     */
    suspend fun importFiles(files: List<Pair<String, String>>): ImportReport {
        val zone = clock().zone
        val at = now()
        var added = 0
        var updated = 0
        var unchanged = 0
        var journalAdded = 0
        val skipped = ArrayList<String>()
        db.withTransaction {
            val docs = files.map { (path, text) -> Triple(path, text, runCatching { MdDoc.parse(text) }.getOrNull()) }
            val people = peopleDao.allWithDeleted().associateBy { it.id }.toMutableMap()
            for ((path, _, doc) in docs) {
                if (doc == null || !MdFiles.isPerson(doc)) continue
                val existing = doc.string("id")?.let { people[it] }
                val incoming = MdFiles.docToPerson(doc, existing, zone, at)
                if (incoming == null) { skipped += path; continue }
                when {
                    existing == null -> { peopleDao.upsert(incoming); added++ }
                    incoming.updatedAt > existing.updatedAt -> { peopleDao.upsert(incoming); updated++ }
                    else -> unchanged++
                }
                people[incoming.id] = incoming
            }
            val known = journal.all().mapTo(HashSet()) { MdFiles.journalKey(it.entryId, it.event, it.day, it.at) }
            for ((path, text, doc) in docs) {
                if (doc == null) { skipped += path; continue }
                if (MdFiles.isPerson(doc)) continue
                if (MdFiles.isJournal(path, doc)) {
                    val rows = JournalTable.parse(text, zone)
                    val fresh = rows.filter { known.add(MdFiles.journalKey(it.entryId, it.event, it.day, it.at)) }
                    journal.insertAll(fresh.map(MdFiles::journalEntity))
                    journalAdded += fresh.size
                    continue
                }
                if (path.startsWith(".pin/")) continue
                val existing = doc.string("id")?.let { entries.get(it) }
                val incoming = MdFiles.docToEntry(doc, existing, people.values.toList(), zone, at)
                if (incoming == null) { skipped += path; continue }
                when {
                    existing == null -> { entries.upsert(incoming); added++ }
                    incoming.updatedAt > existing.updatedAt -> { entries.upsert(incoming); updated++ }
                    else -> unchanged++
                }
            }
            if (added + updated + journalAdded > 0) touch(at)
        }
        // Ничего нового — без уведомления об изменении (иначе синхронизация с файлом зациклится).
        if (added + updated + journalAdded > 0) {
            rollover()
            onChanged?.invoke()
        }
        return ImportReport(added, updated, unchanged, journalAdded, skipped)
    }

    companion object {
        /** entryId записи фокуса без задачи. */
        const val FOCUS_NO_TASK = "focus"

        fun JournalEntity.toStatEvent() =
            StatEvent(entryId, event, day, EntryType.parse(kind) ?: EntryType.ONE_TIME, priority, rollovers, onTime)

        /** События дня: праздники сегодня; и напоминания о ближайших поводах. Чистая функция над записями. */
        fun occasionNotices(all: List<EntryEntity>, people: List<PersonEntity>, today: Long): Pair<List<String>, List<OccasionNotice>> {
            val names = people.associate { it.id to it.name }
            val occasionEntries = all.filter {
                !it.deleted && !it.disabled && (it.entryType == EntryType.BIRTHDAY || it.entryType == EntryType.HOLIDAY) && it.dueDay != null
            }
            val holidaysToday = occasionEntries.filter { it.entryType == EntryType.HOLIDAY && it.dueDay == today }.map { "🎉 " + it.title }
            val occasions = occasionEntries.map { e ->
                Occasion(
                    entryId = e.id,
                    type = e.entryType,
                    title = if (e.entryType == EntryType.BIRTHDAY) names[e.personId] ?: e.title else e.title,
                    day = e.dueDay!!,
                    personId = e.personId,
                    age = if (e.entryType == EntryType.BIRTHDAY) Occasions.ageOn(e.birthYear, e.dueDay) else null,
                )
            }
            val byId = occasionEntries.associateBy { it.id }
            val gifts = all.filter { !it.deleted && it.entryType == EntryType.GIFT }
                .map { GiftInfo(it.id, it.occasionId, it.personId, GiftStatus.parse(it.giftStatus)) }
            val notices = Occasions.notices(occasions, { Occasions.parseRemindDays(byId[it.entryId]?.remindDays) }, gifts, today)
            return holidaysToday to notices
        }

        fun normalizeTags(tags: List<String>): String =
            tags.map { it.trim().removePrefix("#").trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

        fun parseTags(text: String): List<String> = text.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
    }
}
