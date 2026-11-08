package com.sonnik.pin.desktop

import com.sonnik.pin.data.Archive
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.data.MdFiles
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.engine.Attachment
import com.sonnik.pin.engine.Attachments
import com.sonnik.pin.engine.Completion
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.Holidays
import com.sonnik.pin.engine.ImportAction
import com.sonnik.pin.engine.PeopleImport
import com.sonnik.pin.engine.Search
import com.sonnik.pin.engine.SharedPerson
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.Lists
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.QuickDraft
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.Reminders
import com.sonnik.pin.engine.Rollover
import com.sonnik.pin.engine.TaskListRef
import com.sonnik.pin.engine.TaskStatus
import com.sonnik.pin.engine.Tags
import java.time.ZoneId
import java.util.UUID

/** Задача из окна правки (как TaskDraft на телефоне). */
data class Draft(
    val title: String,
    val body: String = "",
    /** Для одноразовой — её день; для периодической — с какого дня начинать повторы. */
    val day: Long,
    val timeMinutes: Int? = null,
    val priority: Priority = Priority.NORMAL,
    val tags: List<String> = emptyList(),
    val repeat: RepeatRule? = null,
    val listId: String? = null,
    val remindBefore: Int = 0,
    /** Напоминания: за сколько минут до времени задачи, до пяти; пусто — не напоминать. */
    val reminders: List<Int> = listOf(remindBefore),
) {
    companion object {
        fun of(e: EntryEntity): Draft = Draft(
            title = e.title,
            body = e.body,
            day = if (e.repeatRule != null) e.repeatAnchor ?: e.dueDay ?: 0 else e.dueDay ?: 0,
            timeMinutes = e.timeMinutes,
            priority = e.entryPriority,
            tags = e.tagList,
            repeat = e.rule,
            listId = e.listId,
            remindBefore = e.remindBefore,
            reminders = e.reminderList,
        )
    }
}

/**
 * Правки на компьютере — те же правила, что у телефона (Repository): чистые функции «данные → новые данные».
 * Перенос невыполненного досчитывается для изменяемой записи, чтобы телефон получил её уже на сегодняшнем дне.
 */
object Edits {
    private fun firstOccurrence(rule: RepeatRule, day: Long): Long = rule.nextAfter(day - 1, day)

    private fun PinData.with(
        entries: List<EntryEntity> = this.entries,
        lists: List<ListEntity> = this.lists,
        journal: List<JournalEntity> = this.journal,
        blobs: Map<String, ByteArray> = this.blobs,
    ) = PinData(entries, people, lists, journal, blobs)

    private fun PinData.put(e: EntryEntity): PinData =
        with(entries = if (byId.containsKey(e.id)) entries.map { if (it.id == e.id) e else it } else entries + e)

    /** Запись с досчитанным переносом на [today] (без событий журнала — их пишет телефон). */
    fun rolled(e: EntryEntity, today: Long): EntryEntity {
        val snapshot = e.toSnapshot() ?: return e
        val r = Rollover.apply(snapshot, today, e.schedule) ?: return e
        return e.copy(dueDay = r.task.currentDay, originalDay = r.task.originalDay, rollovers = r.task.rollovers)
    }

    fun create(data: PinData, draft: Draft, now: Long): Pair<PinData, String> {
        val id = UUID.randomUUID().toString()
        val rule = draft.repeat
        val first = rule?.let { firstOccurrence(it, draft.day) } ?: draft.day
        val e = EntryEntity(
            id = id,
            type = (if (rule != null) EntryType.RECURRING else EntryType.ONE_TIME).name,
            title = draft.title.trim(),
            body = draft.body,
            dueDay = first,
            originalDay = first,
            timeMinutes = draft.timeMinutes,
            priority = draft.priority.level,
            tags = Tags.normalize(draft.tags),
            createdAt = now,
            updatedAt = now,
            repeatRule = rule?.let { RepeatRule.format(it) },
            repeatAnchor = rule?.let { draft.day },
            remindBefore = Reminders.normalize(draft.reminders).firstOrNull() ?: 0,
            reminders = Reminders.format(draft.reminders),
            listId = draft.listId,
        )
        return data.put(e) to id
    }

    /** Быстрое добавление строкой; «@дом» — список (новый создаётся). */
    fun quickAdd(data: PinData, d: QuickDraft, now: Long): Pair<PinData, String> {
        var current = data
        val listId = d.list?.let { name -> listIdFor(current, name, now).also { current = it.first }.second }
        return create(current, Draft(title = d.title, day = d.day, timeMinutes = d.timeMinutes, priority = d.priority, tags = d.tags, listId = listId), now)
    }

    fun listIdFor(data: PinData, name: String, now: Long): Pair<PinData, String> {
        val live = data.lists.filter { !it.deleted }
        Lists.match(live.map { TaskListRef(it.id, it.name) }, name)?.let { return data to it.id }
        val id = UUID.randomUUID().toString()
        val list = ListEntity(
            id = id,
            name = name.trim().replaceFirstChar { it.titlecase() },
            color = ListColor.next(live.map { it.color }).name,
            sortOrder = (live.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAt = now,
            updatedAt = now,
        )
        return data.with(lists = data.lists + list) to id
    }

    /** Правка. Сменили дату или правило повтора — новый план: исходный день и счётчик переносов сбрасываются. */
    fun update(data: PinData, id: String, draft: Draft, today: Long, now: Long): PinData {
        val old = rolled(data.byId[id] ?: return data, today)
        val rule = draft.repeat
        val ruleText = rule?.let { RepeatRule.format(it) }
        val oldStart = if (old.repeatRule != null) old.repeatAnchor ?: old.dueDay else old.dueDay
        val replanned = oldStart != draft.day || ruleText != old.repeatRule
        val first = rule?.let { firstOccurrence(it, draft.day) } ?: draft.day
        return data.put(
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
                tags = Tags.normalize(draft.tags),
                repeatRule = ruleText,
                repeatAnchor = rule?.let { draft.day },
                remindBefore = Reminders.normalize(draft.reminders).firstOrNull() ?: 0,
                reminders = Reminders.format(draft.reminders),
                snoozeUntil = null,
                listId = draft.listId,
                updatedAt = now,
            ),
        )
    }

    fun delete(data: PinData, id: String, now: Long): PinData {
        val old = data.byId[id] ?: return data
        return data.put(old.copy(deleted = true, updatedAt = now))
    }

    /** Отметить выполнение или снять отметку (как setDone на телефоне). */
    fun setDone(data: PinData, id: String, done: Boolean, today: Long, now: Long): PinData {
        val old = rolled(data.byId[id] ?: return data, today)
        val schedule = old.schedule
        return when {
            done && old.entryStatus == TaskStatus.OPEN -> {
                val snapshot = old.toSnapshot() ?: return data
                val event = JournalEntity(
                    entryId = id,
                    event = JournalEvent.DONE,
                    day = today,
                    at = now,
                    kind = old.type,
                    priority = old.priority,
                    rollovers = old.rollovers,
                    onTime = Completion.isOnTime(snapshot, today),
                    occurrenceDay = snapshot.originalDay,
                )
                val updated = if (schedule != null) {
                    val next = Rollover.nextOccurrence(snapshot, today, schedule)
                    old.copy(dueDay = next.currentDay, originalDay = next.originalDay, rollovers = 0, completedAt = now, completedDay = today, updatedAt = now)
                } else {
                    old.copy(status = TaskStatus.DONE.name, completedAt = now, completedDay = today, updatedAt = now)
                }
                data.put(updated).with(journal = data.journal + event)
            }
            !done && schedule != null && old.completedDay != null -> {
                val last = lastDone(data, id) ?: return data
                val occurrence = last.occurrenceDay ?: last.day
                data.put(
                    old.copy(
                        originalDay = occurrence,
                        dueDay = occurrence + last.rollovers,
                        rollovers = last.rollovers,
                        completedAt = null,
                        completedDay = null,
                        updatedAt = now,
                    ),
                ).let { it.with(journal = it.journal.withRemoved(last)) }
            }
            !done && old.entryStatus == TaskStatus.DONE -> {
                val last = lastDone(data, id)
                data.put(old.copy(status = TaskStatus.OPEN.name, completedAt = null, completedDay = null, updatedAt = now))
                    .let { if (last == null) it else it.with(journal = it.journal.withRemoved(last)) }
            }
            else -> data
        }
    }

    /** Перенести на [day] (как «на завтра» и «на дату» в разборе): у одноразовой — новый исходный день. */
    fun reschedule(data: PinData, id: String, day: Long, now: Long): PinData {
        val old = data.byId[id] ?: return data
        val recurring = old.schedule != null
        return data.put(old.copy(dueDay = day, originalDay = if (recurring) old.originalDay else day, rollovers = 0, updatedAt = now))
    }

    /** Задача уходит в планы «когда-нибудь». */
    fun moveToPlans(data: PinData, id: String, now: Long): PinData {
        val old = data.byId[id] ?: return data
        return data.put(
            old.copy(
                type = EntryType.PLAN.name, dueDay = null, originalDay = null, timeMinutes = null, rollovers = 0,
                repeatRule = null, repeatAnchor = null, planScope = PlanScope.format(PlanScope.Someday), updatedAt = now,
            ),
        )
    }

    /** Пропустить текущий повтор периодической: в журнал — пропуск, задача — на следующий повтор. */
    fun skipOccurrence(data: PinData, id: String, today: Long, now: Long): PinData {
        val old = rolled(data.byId[id] ?: return data, today)
        val schedule = old.schedule ?: return data
        val snapshot = old.toSnapshot() ?: return data
        val (next, missed) = Rollover.skipOccurrence(snapshot, today, schedule)
        val event = JournalEntity(
            entryId = id, event = JournalEvent.MISSED, day = missed.occurrenceDay, at = now, kind = old.type,
            priority = old.priority, rollovers = missed.rollovers, occurrenceDay = missed.occurrenceDay,
        )
        return data.put(old.copy(dueDay = next.currentDay, originalDay = next.originalDay, rollovers = 0, updatedAt = now))
            .let { it.with(journal = it.journal + event) }
    }

    /** Новое описание (пункты списка отмечаются прямо в строке задачи). */
    fun setBody(data: PinData, id: String, body: String, now: Long): PinData {
        val old = data.byId[id] ?: return data
        if (old.body == body) return data
        return data.put(old.copy(body = body, updatedAt = now))
    }

    /** Сменить важность (матрица Эйзенхауэра). */
    fun setPriority(data: PinData, id: String, priority: Priority, now: Long): PinData {
        val old = data.byId[id] ?: return data
        return data.put(old.copy(priority = priority.level, updatedAt = now))
    }

    /** Записать минуты фокус-таймера в журнал (как на телефоне: событие FOCUS, минуты — в rollovers). */
    fun logFocus(data: PinData, taskId: String?, minutes: Int, startedAt: Long, day: Long): PinData {
        if (minutes <= 0) return data
        val task = taskId?.let(data.byId::get)
        val event = JournalEntity(
            entryId = taskId ?: FOCUS_NO_TASK, event = JournalEvent.FOCUS, day = day, at = startedAt,
            kind = task?.type ?: JournalEvent.FOCUS, priority = task?.priority ?: Priority.NORMAL.level, rollovers = minutes,
        )
        return data.with(journal = data.journal + event)
    }

    /** Фокус без задачи — тот же id, что у телефона. */
    const val FOCUS_NO_TASK = "focus"

    private fun lastDone(data: PinData, id: String): JournalEntity? =
        data.journal.filter { it.entryId == id && it.event == JournalEvent.DONE }.maxByOrNull { it.at }

    fun addAttachment(data: PinData, id: String, name: String, bytes: ByteArray, now: Long): PinData {
        require(bytes.size <= Attachments.MAX_BYTES) { "файл больше 10 МБ" }
        val used = data.blobs.values.sumOf { it.size.toLong() }
        require(used + bytes.size <= Attachments.MAX_TOTAL_BYTES) {
            "вложения уже занимают ${Attachments.megabytes(used)} МБ, а всего в Pin.zip помещается 50 МБ"
        }
        val old = data.byId[id] ?: return data
        val a = Attachment(UUID.randomUUID().toString().take(8), Attachments.safeName(name))
        val list = Attachments.parse(old.attachments) + a
        return data.put(old.copy(attachments = Attachments.format(list), updatedAt = now)).let { it.with(blobs = it.blobs + (a.path to bytes)) }
    }

    fun removeAttachment(data: PinData, id: String, attachmentId: String, now: Long): PinData {
        val old = data.byId[id] ?: return data
        val list = Attachments.parse(old.attachments)
        val gone = list.firstOrNull { it.id == attachmentId } ?: return data
        return data.put(old.copy(attachments = Attachments.format(list - gone), updatedAt = now)).let { it.with(blobs = it.blobs - gone.path) }
    }

    // ---------- Люди и дни рождения ----------

    /** День рождения: год может быть неизвестен. */
    data class Birthday(val month: Int, val day: Int, val year: Int?)

    /** День рождения человека из его записи BIRTHDAY. */
    fun birthdayOf(data: PinData, personId: String): Birthday? {
        val e = data.entries.firstOrNull { !it.deleted && it.entryType == EntryType.BIRTHDAY && it.personId == personId } ?: return null
        val rule = e.rule as? RepeatRule.Yearly ?: return null
        return Birthday(rule.month, rule.day, e.birthYear)
    }

    /** Сохранить человека и его день рождения (null — без дня рождения), как savePerson на телефоне. */
    fun savePerson(
        data: PinData,
        id: String?,
        name: String,
        relation: String,
        note: String,
        birthday: Birthday?,
        remindDays: List<Int>,
        today: Long,
        now: Long,
    ): Pair<PinData, String> {
        val personId = id ?: UUID.randomUUID().toString()
        val old = data.people.firstOrNull { it.id == personId }
        val person = (old ?: PersonEntity(personId, name, Relation.key(relation), createdAt = now, updatedAt = now))
            .copy(name = name.trim(), relation = Relation.key(relation), note = note, updatedAt = now, deleted = false)
        var result = data.withPeople(if (old == null) data.people + person else data.people.map { if (it.id == personId) person else it })
        val existing = result.entries.firstOrNull { it.entryType == EntryType.BIRTHDAY && it.personId == personId }
        if (birthday == null) {
            existing?.let { result = result.put(it.copy(deleted = true, updatedAt = now)) }
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
                createdAt = now,
                updatedAt = now,
            )
            result = result.put(
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
                    updatedAt = now,
                ),
            )
        }
        return result to personId
    }

    /** Удалить человека вместе с его днём рождения и подарками (пометкой). */
    fun deletePerson(data: PinData, id: String, now: Long): PinData {
        val person = data.people.firstOrNull { it.id == id } ?: return data
        val people = data.people.map { if (it.id == id) person.copy(deleted = true, updatedAt = now) else it }
        val entries = data.entries.map { if (it.personId == id && !it.deleted) it.copy(deleted = true, updatedAt = now) else it }
        return PinData(entries, people, data.lists, data.journal, data.blobs)
    }

    private fun PinData.withPeople(people: List<PersonEntity>) = PinData(entries, people, lists, journal, blobs)

    /** Имя → есть ли день рождения: для плана импорта (как на телефоне). */
    fun peopleBirthdays(data: PinData): Map<String, Boolean> {
        val withBirthday = data.entries.filter { it.entryType == EntryType.BIRTHDAY && !it.deleted }.mapNotNull { it.personId }.toSet()
        return data.people.filter { !it.deleted }.associate { it.name to (it.id in withBirthday) }
    }

    /** Люди для «Поделиться»: имя, категория, заметка, день рождения и напоминания. */
    fun sharedPeople(data: PinData, ids: Collection<String>): List<SharedPerson> =
        data.people.filter { !it.deleted && it.id in ids }.sortedBy { it.name }.map { p ->
            val b = data.entries.firstOrNull { it.entryType == EntryType.BIRTHDAY && !it.deleted && it.personId == p.id }
            val rule = b?.rule as? RepeatRule.Yearly
            SharedPerson(p.name, p.relation, p.note, rule?.month, rule?.day, b?.birthYear, b?.let { Occasions.parseRemindDays(it.remindDays) }.orEmpty())
        }

    /** Добавить выбранных людей из файла по плану [PeopleImport.plan]: новых — целиком, у своих — только день рождения. */
    fun importPeople(data: PinData, items: List<SharedPerson>, today: Long, now: Long): Pair<PinData, Int> {
        var result = data
        var done = 0
        for ((p, action) in PeopleImport.plan(peopleBirthdays(data), items)) {
            val birthday = if (p.hasBirthday) Birthday(p.month!!, p.day!!, p.year) else null
            val remind = p.remindDays.ifEmpty { listOf(7, 1) }
            when (action) {
                ImportAction.ADD -> result = savePerson(result, null, p.name, p.relation, p.note, birthday, remind, today, now).first
                ImportAction.ADD_BIRTHDAY -> {
                    val own = result.people.firstOrNull { !it.deleted && Search.normalize(it.name) == Search.normalize(p.name) } ?: continue
                    result = savePerson(result, own.id, own.name, own.relation, own.note, birthday, remind, today, now).first
                }
                ImportAction.SKIP -> continue
            }
            done++
        }
        return result to done
    }

    // ---------- Подарки ----------

    data class Gift(
        val title: String,
        val personId: String?,
        val occasionId: String?,
        val price: String,
        val link: String,
        val status: GiftStatus,
        val body: String = "",
    )

    fun saveGift(data: PinData, id: String?, gift: Gift, now: Long): Pair<PinData, String> {
        val giftId = id ?: UUID.randomUUID().toString()
        val old = data.byId[giftId]
        val e = (old ?: EntryEntity(id = giftId, type = EntryType.GIFT.name, title = "", createdAt = now, updatedAt = now)).copy(
            title = gift.title.trim(),
            personId = gift.personId,
            occasionId = gift.occasionId,
            price = gift.price.trim().ifEmpty { null },
            link = gift.link.trim().ifEmpty { null },
            giftStatus = gift.status.name,
            body = gift.body,
            updatedAt = now,
        )
        return data.put(e) to giftId
    }

    fun setGiftStatus(data: PinData, id: String, status: GiftStatus, now: Long): PinData {
        val old = data.byId[id] ?: return data
        return data.put(old.copy(giftStatus = status.name, updatedAt = now))
    }

    // ---------- Праздники ----------

    /** Свой праздник (или правка своего): дата каждый год. */
    fun saveHoliday(data: PinData, id: String?, title: String, month: Int, day: Int, today: Long, now: Long): PinData {
        val rule = RepeatRule.Yearly(month, day)
        val first = rule.nextAfter(today - 1, today)
        val old = id?.let(data.byId::get)
        return data.put(
            (old ?: EntryEntity(id = UUID.randomUUID().toString(), type = EntryType.HOLIDAY.name, title = "", createdAt = now, updatedAt = now))
                .copy(title = title.trim(), dueDay = first, originalDay = first, repeatRule = RepeatRule.format(rule), repeatAnchor = first, updatedAt = now),
        )
    }

    /** Включить или выключить запись (праздник): данные остаются. */
    fun setDisabled(data: PinData, id: String, disabled: Boolean, now: Long): PinData {
        val old = data.byId[id] ?: return data
        return data.put(old.copy(disabled = disabled, updatedAt = now))
    }

    /**
     * Встроенные праздники РФ, которых ещё нет в файле, — как на телефоне (те же id, поэтому телефон не задвоит).
     * Выключенные не трогаются.
     */
    fun seedHolidays(data: PinData, today: Long, now: Long): PinData {
        var result = data
        for (h in Holidays.RU) {
            if (result.byId.containsKey(h.entryId)) continue
            val first = h.rule.nextAfter(today - 1, today)
            result = result.put(
                EntryEntity(
                    id = h.entryId, type = EntryType.HOLIDAY.name, title = h.title, dueDay = first, originalDay = first,
                    repeatRule = RepeatRule.format(h.rule), repeatAnchor = first, createdAt = now, updatedAt = now,
                ),
            )
        }
        return result
    }

    // ---------- Списки ----------

    fun saveList(data: PinData, id: String?, name: String, color: ListColor, now: Long): Pair<PinData, String> {
        val listId = id ?: UUID.randomUUID().toString()
        val old = data.lists.firstOrNull { it.id == listId }
        val order = old?.sortOrder ?: ((data.lists.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        val list = (old ?: ListEntity(listId, name, color.name, order, createdAt = now, updatedAt = now))
            .copy(name = name.trim(), color = color.name, updatedAt = now, deleted = false)
        return data.with(lists = if (old == null) data.lists + list else data.lists.map { if (it.id == listId) list else it }) to listId
    }

    /** Удалить список: задачи остаются, только без списка. */
    fun deleteList(data: PinData, id: String, now: Long): PinData {
        val old = data.lists.firstOrNull { it.id == id } ?: return data
        val lists = data.lists.map { if (it.id == id) old.copy(deleted = true, updatedAt = now) else it }
        val entries = data.entries.map { if (it.listId == id) it.copy(listId = null, updatedAt = now) else it }
        return data.with(entries = entries, lists = lists)
    }

    // ---------- Планы ----------

    fun savePlan(data: PinData, id: String?, title: String, body: String, scope: PlanScope, tags: List<String>, now: Long): Pair<PinData, String> {
        val planId = id ?: UUID.randomUUID().toString()
        val old = data.byId[planId]
        val plan = (old ?: EntryEntity(id = planId, type = EntryType.PLAN.name, title = "", createdAt = now, updatedAt = now)).copy(
            title = title.trim(),
            body = body,
            planScope = PlanScope.format(scope),
            tags = Tags.normalize(tags),
            updatedAt = now,
        )
        return data.put(plan) to planId
    }

    /** План становится одноразовой задачей на [day] (запись та же — история и id сохраняются). */
    fun planToTask(data: PinData, id: String, day: Long, timeMinutes: Int?, now: Long): PinData {
        val old = data.byId[id] ?: return data
        return data.put(
            old.copy(
                type = EntryType.ONE_TIME.name, dueDay = day, originalDay = day, planScope = null, rollovers = 0,
                timeMinutes = timeMinutes, updatedAt = now,
            ),
        )
    }

    // ---------- Привычки ----------

    /** Создать или изменить привычку; [rule] = null — каждый день. Новая ведётся с сегодняшнего дня. */
    fun saveHabit(data: PinData, id: String?, title: String, rule: RepeatRule?, remindAt: Int?, today: Long, now: Long): Pair<PinData, String> {
        val habitId = id ?: UUID.randomUUID().toString()
        val old = data.byId[habitId]
        val habit = (old ?: EntryEntity(id = habitId, type = EntryType.HABIT.name, title = "", repeatAnchor = today, createdAt = now, updatedAt = now))
            .copy(title = title.trim(), repeatRule = rule?.let { RepeatRule.format(it) }, timeMinutes = remindAt, updatedAt = now)
        return data.put(habit) to habitId
    }

    /** Отметить привычку за [day] или снять отметку. Будущие дни не отмечаются. */
    fun toggleHabit(data: PinData, id: String, day: Long, today: Long, now: Long): PinData {
        if (day > today) return data
        val habit = data.byId[id] ?: return data
        val existing = data.journal.firstOrNull { it.entryId == id && it.event == JournalEvent.DONE && it.day == day }
        val journal = if (existing != null) {
            data.journal.withRemoved(existing)
        } else {
            data.journal + JournalEntity(
                entryId = id,
                event = JournalEvent.DONE,
                day = day,
                at = now,
                kind = EntryType.HABIT.name,
                priority = habit.priority,
                rollovers = 0,
                onTime = day == today,
                occurrenceDay = day,
            )
        }
        return PinData(data.entries, data.people, data.lists, journal, data.blobs)
    }

    /**
     * Слияние с тем, что сейчас лежит в файле (телефон мог успеть записать своё): запись, человек и список —
     * у кого `updated` новее; журнал — без дублей; вложения — все.
     */
    fun merge(local: PinData, file: PinData): PinData {
        fun <T> pick(a: List<T>, b: List<T>, id: (T) -> String, updated: (T) -> Long): List<T> {
            val result = LinkedHashMap<String, T>()
            for (x in a + b) {
                val prev = result[id(x)]
                if (prev == null || updated(x) > updated(prev)) result[id(x)] = x
            }
            return result.values.toList()
        }
        // Снятые отметки («надгробия») убирают свои события, откуда бы те ни пришли.
        val journal = MdFiles.purgeRemoved(local.journal + file.journal)
        return PinData(
            entries = pick(local.entries, file.entries, { it.id }, { it.updatedAt }),
            people = pick(local.people, file.people, { it.id }, { it.updatedAt }),
            lists = pick(local.lists, file.lists, { it.id }, { it.updatedAt }),
            journal = journal,
            blobs = file.blobs + local.blobs,
        )
    }

    /** Снять событие: оно уходит, вместо него — «надгробие», чтобы снятие доехало до других устройств. */
    private fun List<JournalEntity>.withRemoved(e: JournalEntity): List<JournalEntity> =
        (this - e) + e.copy(event = JournalEvent.removed(e.event), onTime = false)

    /** Архив Pin.zip: те же файлы и порядок, что пишет телефон, и вложения неудалённых записей. */
    fun toBytes(data: PinData, zone: ZoneId): ByteArray {
        val files = MdFiles.export(data.entries, data.people, data.journal, zone, includeDeleted = true, lists = data.lists)
        val wanted = data.entries.filter { !it.deleted }.flatMap { Attachments.parse(it.attachments) }.mapTo(HashSet()) { it.path }
        return Archive.toBytes(files, data.blobs.filterKeys { it in wanted }.toList())
    }
}
