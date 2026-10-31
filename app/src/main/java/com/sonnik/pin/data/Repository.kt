package com.sonnik.pin.data

import androidx.room.withTransaction
import com.sonnik.pin.engine.Completion
import com.sonnik.pin.engine.EntryType
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
)

class PinRepository(
    private val db: PinDatabase,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    private val entries = db.entries()
    private val journal = db.journal()
    private val settingsDao = db.settings()

    /** Вызывается после любого изменения данных — переставить уведомления, обновить виджет. */
    var onChanged: (() -> Unit)? = null

    val datedEntries: Flow<List<EntryEntity>> = entries.observeDated()

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

    companion object {
        fun normalizeTags(tags: List<String>): String =
            tags.map { it.trim().removePrefix("#").trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

        fun parseTags(text: String): List<String> = text.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
    }
}
