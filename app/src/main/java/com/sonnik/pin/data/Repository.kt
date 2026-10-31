package com.sonnik.pin.data

import androidx.room.withTransaction
import com.sonnik.pin.engine.Completion
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.LogicalDay
import com.sonnik.pin.engine.Priority
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
    val day: Long,
    val timeMinutes: Int? = null,
    val priority: Priority = Priority.NORMAL,
    val tags: List<String> = emptyList(),
)

class PinRepository(
    private val db: PinDatabase,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    private val entries = db.entries()
    private val journal = db.journal()
    private val settingsDao = db.settings()

    val datedEntries: Flow<List<EntryEntity>> = entries.observeDated()

    val settings: Flow<SettingsEntity> = settingsDao.observe().map { it ?: SettingsEntity() }

    suspend fun settings(): SettingsEntity = settingsDao.get() ?: SettingsEntity()

    suspend fun logicalDay(): LogicalDay = LogicalDay(clock(), settings().dayBoundaryMinutes)

    private fun now(): Long = clock().millis()

    private suspend fun touch(at: Long = now()) {
        settingsDao.put(settings().copy(lastChangedAt = at))
    }

    /**
     * Досчитывает перенос невыполненных задач до сегодняшнего дня. Безопасно вызывать сколько угодно раз.
     * @return сколько задач перенесено.
     */
    suspend fun rollover(): Int = db.withTransaction {
        val day = logicalDay()
        val today = day.today()
        val stale = entries.openBefore(today)
        val byId = stale.associateBy { it.id }
        val results = Rollover.applyAll(stale.mapNotNull { it.toSnapshot() }, today)
        val at = now()
        if (results.isNotEmpty()) {
            entries.upsertAll(
                results.map { r ->
                    byId.getValue(r.task.id).copy(dueDay = r.task.currentDay, rollovers = r.task.rollovers, updatedAt = at)
                },
            )
            journal.insertAll(
                results.flatMap { r ->
                    val entry = byId.getValue(r.task.id)
                    r.events.mapIndexed { i, e ->
                        JournalEntity(
                            entryId = e.taskId,
                            event = JournalEvent.ROLLED,
                            day = e.fromDay,
                            // Время события — граница дня, когда перенос «случился», а не момент досчёта.
                            at = day.startOf(e.toDay),
                            kind = entry.type,
                            priority = entry.priority,
                            rollovers = entry.rollovers + i + 1,
                        )
                    }
                },
            )
        }
        val s = settings()
        settingsDao.put(s.copy(lastRolloverDay = today, lastChangedAt = if (results.isEmpty()) s.lastChangedAt else at))
        results.size
    }

    suspend fun get(id: String): EntryEntity? = entries.get(id)

    suspend fun create(draft: TaskDraft): String {
        val at = now()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            entries.upsert(
                EntryEntity(
                    id = id,
                    type = EntryType.ONE_TIME.name,
                    title = draft.title.trim(),
                    body = draft.body,
                    dueDay = draft.day,
                    originalDay = draft.day,
                    timeMinutes = draft.timeMinutes,
                    priority = draft.priority.level,
                    tags = normalizeTags(draft.tags),
                    createdAt = at,
                    updatedAt = at,
                ),
            )
            touch(at)
        }
        return id
    }

    /** Правка. Если пользователь сам сменил дату — это новый план: исходный день и счётчик переносов сбрасываются. */
    suspend fun update(id: String, draft: TaskDraft) = db.withTransaction {
        val old = entries.get(id) ?: return@withTransaction
        val at = now()
        val replanned = old.dueDay != draft.day
        entries.upsert(
            old.copy(
                title = draft.title.trim(),
                body = draft.body,
                dueDay = draft.day,
                originalDay = if (replanned) draft.day else old.originalDay,
                rollovers = if (replanned) 0 else old.rollovers,
                timeMinutes = draft.timeMinutes,
                priority = draft.priority.level,
                tags = normalizeTags(draft.tags),
                updatedAt = at,
            ),
        )
        touch(at)
    }

    suspend fun delete(id: String) = db.withTransaction {
        val old = entries.get(id) ?: return@withTransaction
        val at = now()
        entries.upsert(old.copy(deleted = true, updatedAt = at))
        touch(at)
    }

    suspend fun setDone(id: String, done: Boolean) = db.withTransaction {
        val old = entries.get(id) ?: return@withTransaction
        val at = now()
        if (done && old.entryStatus == TaskStatus.OPEN) {
            val day = logicalDay().dayOf(at)
            val snapshot = old.toSnapshot()
            val onTime = snapshot != null && Completion.isOnTime(snapshot, day)
            entries.upsert(old.copy(status = TaskStatus.DONE.name, completedAt = at, completedDay = day, updatedAt = at))
            journal.insert(
                JournalEntity(
                    entryId = id,
                    event = JournalEvent.DONE,
                    day = day,
                    at = at,
                    kind = old.type,
                    priority = old.priority,
                    rollovers = old.rollovers,
                    onTime = onTime,
                ),
            )
        } else if (!done && old.entryStatus == TaskStatus.DONE) {
            old.completedDay?.let { journal.delete(id, JournalEvent.DONE, it) }
            // Если день выполнения уже прошёл, задача вернётся на сегодня при следующем переносе.
            entries.upsert(old.copy(status = TaskStatus.OPEN.name, completedAt = null, completedDay = null, updatedAt = at))
        } else {
            return@withTransaction
        }
        touch(at)
    }

    suspend fun setDayBoundary(minutes: Int) {
        settingsDao.put(settings().copy(dayBoundaryMinutes = minutes.coerceIn(0, LogicalDay.MINUTES_PER_DAY - 1)))
    }

    companion object {
        fun normalizeTags(tags: List<String>): String =
            tags.map { it.trim().removePrefix("#").trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

        fun parseTags(text: String): List<String> = text.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
    }
}
