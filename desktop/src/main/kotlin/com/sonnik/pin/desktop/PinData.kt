package com.sonnik.pin.desktop

import com.sonnik.pin.data.Archive
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.JournalEvent
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.data.MdFiles
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.engine.Attachment
import com.sonnik.pin.engine.CalendarEvent
import com.sonnik.pin.engine.DayMarks
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.HabitInfo
import com.sonnik.pin.engine.Habits
import com.sonnik.pin.engine.HolidayKind
import com.sonnik.pin.engine.Holidays
import com.sonnik.pin.engine.JournalTable
import com.sonnik.pin.engine.MarkItem
import com.sonnik.pin.engine.MarkKind
import com.sonnik.pin.engine.MdDoc
import com.sonnik.pin.engine.MonthMarks
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.Rollover
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus
import com.sonnik.pin.engine.TodayBoard
import java.io.ByteArrayInputStream
import java.time.ZoneId

/**
 * Всё содержимое Pin.zip в памяти: записи, люди, списки, журнал и вложения.
 * Чистые функции без окон — те же правила, что на телефоне (вкладки «Сегодня» и «Календарь»).
 */
class PinData(
    val entries: List<EntryEntity>,
    val people: List<PersonEntity>,
    val lists: List<ListEntity>,
    val journal: List<JournalEntity>,
    /** Вложения: путь в архиве (attachments/<id>/<имя>) → байты. */
    val blobs: Map<String, ByteArray>,
) {
    val byId: Map<String, EntryEntity> = entries.associateBy { it.id }
    val listsById: Map<String, ListEntity> = lists.filter { !it.deleted }.associateBy { it.id }
    private val names: Map<String, String> = people.filter { !it.deleted }.associate { it.id to it.name }

    /** Видимые записи: не удалённые и не выключенные. */
    private val live: List<EntryEntity> = entries.filter { !it.deleted && !it.disabled }

    fun blob(a: Attachment): ByteArray? = blobs[a.path]

    /**
     * Экран «Сегодня». Если телефон давно не синхронизировался, перенос досчитывается здесь же —
     * только для показа, в файл ничего не пишется.
     */
    fun board(today: Long): TodayBoard {
        val dated = live.filter { it.dueDay != null }
        val snapshots = dated.mapNotNull { it.toSnapshot() }.map { s ->
            Rollover.apply(s, today, byId[s.id]?.schedule)?.task ?: s
        }
        val doneRecurring = dated
            .filter { it.schedule != null && it.completedDay == today }
            .mapNotNull { e -> e.toSnapshot()?.copy(status = TaskStatus.DONE, currentDay = today, originalDay = today, rollovers = 0) }
        return TodayBoard.build(snapshots + doneRecurring, today, doneDayOf = { byId[it.id]?.completedDay })
    }

    /** Отметки привычек: id → дни. */
    val habitDays: Map<String, Set<Long>> by lazy {
        journal.filter { it.event == JournalEvent.DONE }.groupBy({ it.entryId }, { it.day }).mapValues { it.value.toSet() }
    }

    fun habitInfo(e: EntryEntity, today: Long): HabitInfo = HabitInfo(e.id, e.rule, e.repeatAnchor ?: today, habitDays[e.id].orEmpty())

    /** Привычки на «Сегодня»: по расписанию на сегодня или уже отмеченные сегодня. */
    fun habitsFor(today: Long): List<Pair<EntryEntity, HabitInfo>> =
        live.filter { it.entryType == EntryType.HABIT }.sortedBy { it.createdAt }
            .map { it to habitInfo(it, today) }
            .filter { (_, info) -> Habits.isScheduled(info, today) || today in info.doneDays }

    /** Подпись записи для экрана: у дня рождения — имя человека. */
    fun titleOf(e: EntryEntity): String =
        if (e.entryType == EntryType.BIRTHDAY) e.personId?.let(names::get)?.let { "🎂 $it" } ?: e.title else e.title

    /** Значки дней [from]..[to] — как у вкладки «Календарь» и виджета. */
    fun monthMarks(from: Long, to: Long, today: Long): Map<Long, DayMarks> {
        val items = ArrayList<MarkItem>()
        for ((day, kind, e) in dayItemsBetween(from, to, today)) {
            items += MarkItem(
                day, kind, e.entryType, e.entryPriority == Priority.HIGH,
                dayOff = e.entryType != EntryType.HOLIDAY || Holidays.kindOf(e.id) == HolidayKind.DAY_OFF,
            )
        }
        return MonthMarks.build(items)
    }

    /** Что стоит на день: открытое (с учётом переноса на сегодня), будущие повторы и выполненное. */
    data class DayItem(val day: Long, val kind: MarkKind, val entry: EntryEntity)

    fun dayItemsBetween(from: Long, to: Long, today: Long): List<DayItem> {
        val result = ArrayList<DayItem>()
        for (raw in live) {
            if (raw.entryType == EntryType.HABIT || raw.dueDay == null) continue
            // Как на телефоне после ночного переноса: невыполненное — на сегодня, прошедший повтор — на следующий.
            val e = Edits.rolled(raw, today)
            val due = e.dueDay ?: continue
            if (e.entryStatus == TaskStatus.OPEN && due in from..to) result += DayItem(due, MarkKind.OPEN, raw)
            val schedule = e.schedule ?: continue
            val start = maxOf(e.originalDay ?: due, due)
            for (d in RepeatRule.occurrences(schedule.rule, schedule.anchor, maxOf(start + 1, from), to)) {
                result += DayItem(d, MarkKind.PROJECTED, raw)
            }
        }
        for (ev in journal) {
            if (ev.event != JournalEvent.DONE || ev.day !in from..to) continue
            val e = byId[ev.entryId] ?: continue
            if (e.deleted || e.entryType == EntryType.HABIT) continue
            result += DayItem(ev.day, MarkKind.DONE, e)
        }
        return result.distinctBy { Triple(it.day, it.kind, it.entry.id) }
    }

    /** Дни рождения и праздники в [from]..[to]. */
    fun monthEvents(from: Long, to: Long): List<CalendarEvent> {
        val result = ArrayList<CalendarEvent>()
        for (raw in live) {
            if (raw.entryType != EntryType.BIRTHDAY && raw.entryType != EntryType.HOLIDAY || raw.dueDay == null) continue
            val e = Edits.rolled(raw, today = from.coerceAtLeast(raw.dueDay))
            val due = e.dueDay ?: continue
            val days = buildList {
                if (e.entryStatus == TaskStatus.OPEN) add(due)
                e.schedule?.let { s -> addAll(RepeatRule.occurrences(s.rule, s.anchor, maxOf(due + 1, from), to)) }
            }.filter { it in from..to }
            for (d in days) {
                result += if (e.entryType == EntryType.BIRTHDAY) {
                    val name = e.personId?.let(names::get) ?: e.title.removePrefix("Поздравить:").trim()
                    CalendarEvent(d, true, name, Occasions.ageOn(e.birthYear, d))
                } else {
                    CalendarEvent(d, false, e.title, emoji = Holidays.kindOf(e.id).emoji)
                }
            }
        }
        return result.sortedBy { it.day }
    }

    /** Ближайшие дни рождения и праздники — для шапки «Сегодня». */
    fun upcomingEvents(today: Long, days: Int = 14): List<CalendarEvent> = monthEvents(today, today + days)

    companion object {
        val EMPTY = PinData(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())

        /** Прочитать Pin.zip. Неизвестные поля и файлы пропускаются; при повторах одной записи побеждает более новая. */
        fun read(bytes: ByteArray, zone: ZoneId, now: Long = System.currentTimeMillis()): PinData {
            val content = Archive.readContent(ByteArrayInputStream(bytes))
            return fromFiles(content.files, content.blobs, zone, now)
        }

        fun fromFiles(files: List<Pair<String, String>>, blobs: List<Pair<String, ByteArray>>, zone: ZoneId, now: Long): PinData {
            val docs = files.map { (path, text) -> Triple(path, text, runCatching { MdDoc.parse(text) }.getOrNull()) }
            val people = LinkedHashMap<String, PersonEntity>()
            val lists = LinkedHashMap<String, ListEntity>()
            val entries = LinkedHashMap<String, EntryEntity>()
            val journal = ArrayList<JournalEntity>()
            val seen = HashSet<String>()
            for ((_, _, doc) in docs) {
                if (doc == null || !MdFiles.isPerson(doc)) continue
                val p = MdFiles.docToPerson(doc, null, zone, now) ?: continue
                if ((people[p.id]?.updatedAt ?: Long.MIN_VALUE) < p.updatedAt) people[p.id] = p
            }
            for ((_, _, doc) in docs) {
                if (doc == null || !MdFiles.isList(doc)) continue
                val l = MdFiles.docToList(doc, null, zone, now) ?: continue
                if ((lists[l.id]?.updatedAt ?: Long.MIN_VALUE) < l.updatedAt) lists[l.id] = l
            }
            for ((path, text, doc) in docs) {
                if (doc == null || MdFiles.isPerson(doc) || MdFiles.isList(doc)) continue
                if (MdFiles.isJournal(path, doc)) {
                    JournalTable.parse(text, zone).forEach { r ->
                        if (seen.add(MdFiles.journalKey(r.entryId, r.event, r.day, r.at))) journal += MdFiles.journalEntity(r)
                    }
                    continue
                }
                if (path.startsWith(".pin/")) continue
                val e = MdFiles.docToEntry(doc, null, people.values.toList(), zone, now, lists.values.toList()) ?: continue
                if ((entries[e.id]?.updatedAt ?: Long.MIN_VALUE) < e.updatedAt) entries[e.id] = e
            }
            return PinData(entries.values.toList(), people.values.toList(), lists.values.toList(), MdFiles.purgeRemoved(journal), blobs.toMap())
        }

        /** Снимок задачи для строки: тот же, что строит [board]. */
        fun snapshotOf(e: EntryEntity): TaskSnapshot? = e.toSnapshot()
    }
}
