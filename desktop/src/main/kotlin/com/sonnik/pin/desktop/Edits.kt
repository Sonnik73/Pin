package com.sonnik.pin.desktop

import com.sonnik.pin.data.Archive
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.data.MdFiles
import com.sonnik.pin.engine.Attachment
import com.sonnik.pin.engine.Attachments
import com.sonnik.pin.engine.Completion
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.Lists
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.QuickDraft
import com.sonnik.pin.engine.RepeatRule
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
            remindBefore = draft.remindBefore,
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
                remindBefore = draft.remindBefore,
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
                ).let { it.with(journal = it.journal - last) }
            }
            !done && old.entryStatus == TaskStatus.DONE -> {
                val last = lastDone(data, id)
                data.put(old.copy(status = TaskStatus.OPEN.name, completedAt = null, completedDay = null, updatedAt = now))
                    .let { if (last == null) it else it.with(journal = it.journal - last) }
            }
            else -> data
        }
    }

    private fun lastDone(data: PinData, id: String): JournalEntity? =
        data.journal.filter { it.entryId == id && it.event == JournalEvent.DONE }.maxByOrNull { it.at }

    fun addAttachment(data: PinData, id: String, name: String, bytes: ByteArray, now: Long): PinData {
        require(bytes.size <= Attachments.MAX_BYTES) { "файл больше 10 МБ" }
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
        val seen = HashSet<String>()
        val journal = (local.journal + file.journal).filter { seen.add(MdFiles.journalKey(it.entryId, it.event, it.day, it.at)) }
        return PinData(
            entries = pick(local.entries, file.entries, { it.id }, { it.updatedAt }),
            people = pick(local.people, file.people, { it.id }, { it.updatedAt }),
            lists = pick(local.lists, file.lists, { it.id }, { it.updatedAt }),
            journal = journal,
            blobs = file.blobs + local.blobs,
        )
    }

    /** Архив Pin.zip: те же файлы и порядок, что пишет телефон, и вложения неудалённых записей. */
    fun toBytes(data: PinData, zone: ZoneId): ByteArray {
        val files = MdFiles.export(data.entries, data.people, data.journal, zone, includeDeleted = true, lists = data.lists)
        val wanted = data.entries.filter { !it.deleted }.flatMap { Attachments.parse(it.attachments) }.mapTo(HashSet()) { it.path }
        return Archive.toBytes(files, data.blobs.filterKeys { it in wanted }.toList())
    }
}
