package com.sonnik.pin.data

import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.JournalTable
import com.sonnik.pin.engine.MdDoc
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.engine.RuText
import com.sonnik.pin.engine.TaskStatus
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Перевод записей, людей и журнала в .md-файлы и обратно (docs/FORMAT.md).
 * Чистые функции без Android: их же использует синхронизация с Google Диском.
 */
object MdFiles {
    /** Файл: путь внутри папки «Pin», содержимое и постоянный ключ (запись, человек или месяц журнала). */
    data class File(val path: String, val text: String, val key: String = "") {
        val folder: String get() = path.substringBeforeLast('/', "")
        val name: String get() = path.substringAfterLast('/')
    }

    fun entryKey(id: String) = "e:$id"
    fun personKey(id: String) = "p:$id"
    fun journalKey(month: String) = "j:$month"

    /** Поля, которые Pin понимает; остальные хранятся в [EntryEntity.extra] и записываются обратно. */
    private val ENTRY_KEYS = setOf(
        "id", "type", "title", "due", "original", "time", "priority", "tags", "rollovers", "status", "completed",
        "completed_day", "repeat", "repeat_from", "person", "person_id", "birth_year", "remind", "gift_status", "price",
        "link", "occasion", "plan", "disabled", "deleted", "created", "updated", "remind_before", "list", "list_id",
    )
    private val PERSON_KEYS = setOf("id", "type", "name", "relation", "created", "updated", "deleted")

    fun folderOf(type: EntryType): String = when (type) {
        EntryType.ONE_TIME, EntryType.RECURRING -> "tasks"
        EntryType.BIRTHDAY -> "birthdays"
        EntryType.HOLIDAY -> "holidays"
        EntryType.PLAN -> "plans"
        EntryType.GIFT -> "gifts"
        EntryType.HABIT -> "habits"
    }

    private fun priorityName(level: Int) = when (Priority.ofLevel(level)) {
        Priority.LOW -> "low"
        Priority.NORMAL -> "normal"
        Priority.HIGH -> "high"
    }

    fun entryToDoc(e: EntryEntity, people: Map<String, PersonEntity>, zone: ZoneId, lists: Map<String, ListEntity> = emptyMap()): MdDoc {
        val f = LinkedHashMap<String, Any>()
        f["id"] = e.id
        f["type"] = e.entryType.name.lowercase()
        f["title"] = e.title
        e.dueDay?.let { f["due"] = MdDoc.formatDay(it) }
        e.originalDay?.takeIf { it != e.dueDay }?.let { f["original"] = MdDoc.formatDay(it) }
        e.timeMinutes?.let { f["time"] = RuText.time(it) }
        if (e.remindBefore > 0) f["remind_before"] = e.remindBefore
        if (e.entryType != EntryType.HOLIDAY && e.entryType != EntryType.GIFT) f["priority"] = priorityName(e.priority)
        if (e.tagList.isNotEmpty()) f["tags"] = e.tagList
        e.listId?.let { id ->
            lists[id]?.let { f["list"] = it.name }
            f["list_id"] = id
        }
        if (e.rollovers > 0) f["rollovers"] = e.rollovers
        if (e.dueDay != null && e.entryType != EntryType.HOLIDAY) f["status"] = e.status.lowercase()
        e.completedAt?.let { f["completed"] = MdDoc.formatInstant(it, zone) }
        e.completedDay?.let { f["completed_day"] = MdDoc.formatDay(it) }
        e.repeatRule?.let { f["repeat"] = it }
        e.repeatAnchor?.let { f["repeat_from"] = MdDoc.formatDay(it) }
        e.personId?.let { id ->
            people[id]?.let { f["person"] = "[[${it.name}]]" }
            f["person_id"] = id
        }
        e.birthYear?.let { f["birth_year"] = it }
        e.remindDays?.let { f["remind"] = it.split(',').filter(String::isNotBlank) }
        e.giftStatus?.let { f["gift_status"] = it.lowercase() }
        e.price?.let { f["price"] = it }
        e.link?.let { f["link"] = it }
        e.occasionId?.let { f["occasion"] = it }
        e.planScope?.let { f["plan"] = it }
        if (e.disabled) f["disabled"] = "true"
        if (e.deleted) f["deleted"] = "true"
        f["created"] = MdDoc.formatInstant(e.createdAt, zone)
        f["updated"] = MdDoc.formatInstant(e.updatedAt, zone)
        decodeExtra(e.extra).forEach { (k, v) -> if (k !in f) f[k] = v }
        return MdDoc(f, e.body)
    }

    /**
     * Запись из файла. Поля, которых в файле нет, берутся из [existing] (или по умолчанию) — файл, где руками
     * оставили только заголовок и дату, не сбрасывает остальное. null — файл не похож на запись.
     */
    fun docToEntry(
        doc: MdDoc,
        existing: EntryEntity?,
        people: List<PersonEntity>,
        zone: ZoneId,
        now: Long,
        lists: List<ListEntity> = emptyList(),
    ): EntryEntity? {
        val title = doc.string("title")
            ?: doc.body.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            ?: existing?.title
            ?: return null
        // Файл без типа и без даты (заметка, созданная руками в Obsidian) становится планом «когда-нибудь».
        val type = EntryType.parse(doc.string("type")) ?: existing?.entryType
            ?: if (doc.string("plan") != null || doc.day("due") == null) EntryType.PLAN else EntryType.ONE_TIME
        val base = existing ?: EntryEntity(
            id = doc.string("id") ?: UUID.randomUUID().toString(),
            type = type.name,
            title = title,
            createdAt = doc.instant("created", zone) ?: now,
            updatedAt = now,
        )
        fun has(key: String) = doc.fields.containsKey(key)
        val personId = doc.string("person_id")
            ?: doc.string("person")?.removePrefix("[[")?.removeSuffix("]]")?.let { name -> people.firstOrNull { it.name == name }?.id }
        val due = if (has("due")) doc.day("due") else base.dueDay
        return base.copy(
            type = type.name,
            title = title,
            body = doc.body.trimEnd('\n'),
            dueDay = due,
            originalDay = if (has("original")) doc.day("original") else if (has("due")) due else base.originalDay,
            timeMinutes = if (has("time")) parseTime(doc.string("time")) else base.timeMinutes,
            listId = when {
                has("list_id") -> doc.string("list_id")
                has("list") -> doc.string("list")?.let { n -> lists.firstOrNull { !it.deleted && it.name.equals(n.trim(), ignoreCase = true) }?.id }
                else -> base.listId
            },
            remindBefore = if (has("remind_before")) doc.int("remind_before")?.coerceIn(0, com.sonnik.pin.engine.Reminders.MAX_ADVANCE) ?: 0 else base.remindBefore,
            priority = if (has("priority")) Priority.parse(doc.string("priority")).level else base.priority,
            tags = if (has("tags")) PinRepository.normalizeTags(doc.list("tags")) else base.tags,
            rollovers = if (has("rollovers")) doc.int("rollovers") ?: 0 else base.rollovers,
            status = if (has("status")) TaskStatus.parse(doc.string("status")).name else base.status,
            completedAt = if (has("completed")) doc.instant("completed", zone) else base.completedAt,
            completedDay = if (has("completed_day")) doc.day("completed_day") else base.completedDay,
            repeatRule = if (has("repeat")) doc.string("repeat") else base.repeatRule,
            repeatAnchor = if (has("repeat_from")) doc.day("repeat_from") else base.repeatAnchor,
            personId = if (has("person_id") || has("person")) personId else base.personId,
            birthYear = if (has("birth_year")) doc.int("birth_year") else base.birthYear,
            remindDays = if (has("remind")) doc.list("remind").joinToString(",") else base.remindDays,
            giftStatus = if (has("gift_status")) doc.string("gift_status")?.uppercase() else base.giftStatus,
            price = if (has("price")) doc.string("price") else base.price,
            link = if (has("link")) doc.string("link") else base.link,
            occasionId = if (has("occasion")) doc.string("occasion") else base.occasionId,
            planScope = if (has("plan")) doc.string("plan") else base.planScope ?: if (type == EntryType.PLAN) "someday" else null,
            disabled = if (has("disabled")) doc.bool("disabled") == true else base.disabled,
            deleted = if (has("deleted")) doc.bool("deleted") == true else base.deleted,
            updatedAt = doc.instant("updated", zone) ?: now,
            extra = encodeExtra(doc.fields.filterKeys { it !in ENTRY_KEYS }),
        )
    }

    private fun parseTime(text: String?): Int? {
        val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text?.trim() ?: return null) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    fun personToDoc(p: PersonEntity, zone: ZoneId): MdDoc {
        val f = LinkedHashMap<String, Any>()
        f["id"] = p.id
        f["type"] = "person"
        f["name"] = p.name
        // Встроенная категория — именем enum маленькими буквами, своя — как есть.
        f["relation"] = Relation.known(p.relation)?.name?.lowercase() ?: p.relation
        if (p.deleted) f["deleted"] = "true"
        f["created"] = MdDoc.formatInstant(p.createdAt, zone)
        f["updated"] = MdDoc.formatInstant(p.updatedAt, zone)
        return MdDoc(f, p.note)
    }

    fun docToPerson(doc: MdDoc, existing: PersonEntity?, zone: ZoneId, now: Long): PersonEntity? {
        val name = doc.string("name") ?: doc.string("title") ?: existing?.name ?: return null
        val base = existing ?: PersonEntity(
            id = doc.string("id") ?: UUID.randomUUID().toString(),
            name = name,
            relation = "OTHER",
            createdAt = doc.instant("created", zone) ?: now,
            updatedAt = now,
        )
        return base.copy(
            name = name,
            relation = doc.string("relation")?.let(Relation::key) ?: base.relation,
            note = doc.body.trimEnd('\n'),
            deleted = if (doc.fields.containsKey("deleted")) doc.bool("deleted") == true else base.deleted,
            updatedAt = doc.instant("updated", zone) ?: now,
        )
    }

    fun listToDoc(l: ListEntity, zone: ZoneId): MdDoc {
        val f = LinkedHashMap<String, Any>()
        f["id"] = l.id
        f["type"] = "list"
        f["name"] = l.name
        f["color"] = l.color.lowercase()
        f["order"] = l.sortOrder
        if (l.deleted) f["deleted"] = "true"
        f["created"] = MdDoc.formatInstant(l.createdAt, zone)
        f["updated"] = MdDoc.formatInstant(l.updatedAt, zone)
        return MdDoc(f, "")
    }

    fun docToList(doc: MdDoc, existing: ListEntity?, zone: ZoneId, now: Long): ListEntity? {
        val name = doc.string("name") ?: doc.string("title") ?: existing?.name ?: return null
        val base = existing ?: ListEntity(
            id = doc.string("id") ?: UUID.randomUUID().toString(),
            name = name,
            color = "BLUE",
            createdAt = doc.instant("created", zone) ?: now,
            updatedAt = now,
        )
        return base.copy(
            name = name,
            color = doc.string("color")?.let { com.sonnik.pin.engine.ListColor.parse(it).name } ?: base.color,
            sortOrder = doc.int("order") ?: base.sortOrder,
            deleted = if (doc.fields.containsKey("deleted")) doc.bool("deleted") == true else base.deleted,
            updatedAt = doc.instant("updated", zone) ?: now,
        )
    }

    fun isList(doc: MdDoc): Boolean = doc.string("type").equals("list", ignoreCase = true)

    fun listKey(id: String) = "l:$id"

    fun isPerson(doc: MdDoc): Boolean = doc.string("type").equals("person", ignoreCase = true)
    fun isJournal(path: String, doc: MdDoc?): Boolean =
        path.startsWith("journal/") || doc?.string("type").equals("journal", ignoreCase = true)

    fun journalRow(j: JournalEntity) = JournalTable.Row(j.day, j.event, j.entryId, j.at, j.kind, j.priority, j.rollovers, j.onTime, j.occurrenceDay)

    fun journalEntity(r: JournalTable.Row) = JournalEntity(
        entryId = r.entryId, event = r.event, day = r.day, at = r.at, kind = r.kind, priority = r.priority,
        rollovers = r.rollovers, onTime = r.onTime, occurrenceDay = r.occurrence,
    )

    /** Ключ события журнала для слияния без дублей. */
    fun journalKey(entryId: String, event: String, day: Long, at: Long) = "$entryId|$event|$day|$at"

    /** Все файлы папки «Pin». Имена уникальны; [includeDeleted] — для синхронизации, где удаления передаются пометкой. */
    fun export(
        entries: List<EntryEntity>,
        people: List<PersonEntity>,
        journal: List<JournalEntity>,
        zone: ZoneId,
        includeDeleted: Boolean,
        lists: List<ListEntity> = emptyList(),
    ): List<File> {
        val files = ArrayList<File>()
        val used = HashSet<String>()
        fun unique(folder: String, base: String, id: String): String {
            var path = "$folder/${MdDoc.fileName(base)}.md"
            if (!used.add(path.lowercase())) {
                path = "$folder/${MdDoc.fileName(base)} (${id.take(8)}).md"
                used.add(path.lowercase())
            }
            return path
        }
        val byId = people.associateBy { it.id }
        val listsById = lists.associateBy { it.id }
        for (l in lists.sortedBy { it.sortOrder }) {
            if (l.deleted && !includeDeleted) continue
            files += File(unique("lists", l.name, l.id), listToDoc(l, zone).render(), listKey(l.id))
        }
        for (p in people.sortedBy { it.name }) {
            if (p.deleted && !includeDeleted) continue
            files += File(unique("people", p.name, p.id), personToDoc(p, zone).render(), personKey(p.id))
        }
        for (e in entries.sortedWith(compareBy({ it.dueDay ?: Long.MAX_VALUE }, { it.title }))) {
            if (e.deleted && !includeDeleted) continue
            val name = if (e.entryType == EntryType.ONE_TIME && e.dueDay != null) "${MdDoc.formatDay(e.dueDay)} ${e.title}" else e.title
            files += File(unique(folderOf(e.entryType), name, e.id), entryToDoc(e, byId, zone, listsById).render(), entryKey(e.id))
        }
        journal.groupBy { LocalDate.ofEpochDay(it.day).toString().take(7) }.toSortedMap().forEach { (month, rows) ->
            files += File("journal/$month.md", JournalTable.render(month, rows.map(::journalRow), zone), journalKey(month))
        }
        return files
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeExtra(map: Map<String, Any>): String? {
        if (map.isEmpty()) return null
        val obj = JsonObject(map.mapValues { (_, v) -> JsonPrimitive(if (v is List<*>) v.joinToString(", ") else v.toString()) })
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decodeExtra(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.parseToJsonElement(text).let { it as JsonObject }.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }
}
