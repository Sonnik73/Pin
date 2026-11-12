package com.sonnik.pin.data

import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.TaskStatus
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertTrue

// Общий для проверок нагрузкой телефона и Pin для Windows (desktop берёт этот файл как есть).

/** Отчёт о замерах: таблица в markdown и проверка лимитов. */
class LoadReport(private val title: String) {
    private data class Row(val name: String, val ms: Long, val limit: Long)

    private val rows = ArrayList<Row>()
    private val notes = ArrayList<String>()

    fun line(text: String) {
        notes += text
        println("[load] $text")
    }

    inline fun <T> measure(name: String, limitMs: Long, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        add(name, (System.nanoTime() - start) / 1_000_000, limitMs)
        return result
    }

    fun add(name: String, ms: Long, limit: Long) {
        rows += Row(name, ms, limit)
        println("[load] $name: $ms мс (лимит $limit)")
    }

    fun write(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("## $title")
                appendLine()
                notes.forEach { appendLine("- $it") }
                appendLine()
                appendLine("| Операция | Время, мс | Лимит, мс | |")
                appendLine("|---|---:|---:|---|")
                rows.forEach { appendLine("| ${it.name} | ${it.ms} | ${it.limit} | ${if (it.ms <= it.limit) "✅" else "❌"} |") }
            },
        )
    }

    fun assertWithinLimits() {
        val slow = rows.filter { it.ms > it.limit }
        assertTrue("Медленно: " + slow.joinToString { "${it.name} ${it.ms} мс > ${it.limit}" }, slow.isEmpty())
    }
}

/** Данные «пять лет активного пользователя»; детерминированы от seed. */
object LoadData {
    class Generated(val entries: List<EntryEntity>, val people: List<PersonEntity>, val lists: List<ListEntity>, val journal: List<JournalEntity>) {
        fun describe() = "записей ${entries.size} (" +
            entries.groupingBy { it.entryType }.eachCount().entries.joinToString { "${it.key.name.lowercase()} ${it.value}" } +
            "), людей ${people.size}, списков ${lists.size}, событий журнала ${journal.size}"
    }

    private val words = listOf("позвонить", "купить", "написать", "оплатить", "забрать", "проверить", "починить", "записаться", "отправить", "прочитать")
    private val objects = listOf("маме", "хлеб", "отчёт", "счёт за свет", "посылку", "машину", "кран", "к врачу", "документы", "книгу")
    private val tags = listOf("дом", "работа", "семья", "здоровье", "деньги", "машина", "дача", "учёба", "покупки", "друзья")

    fun generate(today: Long, now: Long, seed: Int = 73): Generated {
        val r = Random(seed)
        val day = 86_400_000L
        val entries = ArrayList<EntryEntity>()
        val journal = ArrayList<JournalEntity>()
        val lists = (0 until 8).map { ListEntity("list$it", "Список $it", "BLUE", sortOrder = it, createdAt = now, updatedAt = now) }
        val people = (0 until 300).map { PersonEntity("p$it", "Человек $it", "FRIEND", "заметка", createdAt = now, updatedAt = now) }

        fun title(i: Int) = "${words[i % words.size]} ${objects[(i / 10) % objects.size]} $i".replaceFirstChar { it.uppercase() }
        fun someTags() = (0 until r.nextInt(0, 3)).map { tags[r.nextInt(tags.size)] }.distinct().joinToString(",")
        fun body(i: Int) = if (i % 3 == 0) "Подробности к задаче $i: " + "текст описания ".repeat(r.nextInt(1, 12)) else ""

        // Выполненные за пять лет.
        for (i in 0 until 15_000) {
            val due = today - r.nextLong(1, 5 * 365)
            val doneDay = due + r.nextLong(0, 4)
            entries += EntryEntity(
                id = "done$i", type = EntryType.ONE_TIME.name, title = title(i), body = body(i), dueDay = doneDay, originalDay = due,
                priority = if (i % 7 == 0) Priority.HIGH.level else Priority.NORMAL.level, tags = someTags(),
                rollovers = (doneDay - due).toInt(), status = TaskStatus.DONE.name, completedAt = doneDay * day, completedDay = doneDay,
                createdAt = due * day, updatedAt = doneDay * day, listId = if (i % 4 == 0) "list${i % 8}" else null,
                timeMinutes = if (i % 5 == 0) 9 * 60 + i % 600 else null,
            )
            journal += JournalEntity(entryId = "done$i", event = JournalEvent.DONE, day = doneDay, at = doneDay * day + 3_600_000, kind = EntryType.ONE_TIME.name, priority = 0, rollovers = (doneDay - due).toInt(), onTime = doneDay == due, occurrenceDay = due)
            for (d in due until doneDay) journal += JournalEntity(entryId = "done$i", event = JournalEvent.ROLLED, day = d, at = (d + 1) * day, kind = EntryType.ONE_TIME.name, priority = 0, rollovers = (d - due + 1).toInt(), occurrenceDay = due)
        }
        // Открытые: часть просрочена, часть впереди.
        for (i in 0 until 1_500) {
            val due = today + r.nextLong(-10, 60)
            entries += EntryEntity(
                id = "open$i", type = EntryType.ONE_TIME.name, title = title(i + 20_000), body = body(i), dueDay = due, originalDay = due,
                tags = someTags(), createdAt = now - 30 * day, updatedAt = now - 30 * day, timeMinutes = if (i % 3 == 0) 8 * 60 + i % 700 else null,
                reminders = if (i % 3 == 0) "0,60" else "", listId = "list${i % 8}",
            )
        }
        // Повторяющиеся.
        val rules = listOf("daily", "weekly mo,we,fr", "monthly 1", "every 3 days", "yearly 03-08")
        for (i in 0 until 300) {
            val anchor = today - r.nextLong(30, 900)
            entries += EntryEntity(
                id = "rec$i", type = EntryType.RECURRING.name, title = "Повтор ${title(i)}", dueDay = today, originalDay = today,
                repeatRule = rules[i % rules.size], repeatAnchor = anchor, createdAt = anchor * day, updatedAt = now - day, tags = someTags(),
            )
        }
        // Привычки: 40 штук, отмечены ~70 % дней за три года.
        for (i in 0 until 40) {
            val anchor = today - 3 * 365
            entries += EntryEntity(id = "habit$i", type = EntryType.HABIT.name, title = "Привычка $i", repeatRule = "daily", repeatAnchor = anchor, createdAt = anchor * day, updatedAt = now)
            for (d in anchor until today) if (r.nextInt(10) < 7) {
                journal += JournalEntity(entryId = "habit$i", event = JournalEvent.DONE, day = d, at = d * day + 20 * 3_600_000, kind = EntryType.HABIT.name, priority = 0, rollovers = 0, onTime = true, occurrenceDay = d)
            }
        }
        // Дни рождения людей.
        for ((i, p) in people.withIndex()) {
            val next = today + r.nextLong(0, 365)
            entries += EntryEntity(
                id = "bd$i", type = EntryType.BIRTHDAY.name, title = "Поздравить: ${p.name}", dueDay = next, originalDay = next,
                repeatRule = java.time.LocalDate.ofEpochDay(next).let { "yearly %02d-%02d".format(it.monthValue, it.dayOfMonth) },
                repeatAnchor = next, personId = p.id, birthYear = 1950 + i % 60, remindDays = "7,1", createdAt = now - 400 * day, updatedAt = now - 400 * day,
            )
        }
        // Планы и важные даты.
        for (i in 0 until 500) {
            entries += EntryEntity(id = "plan$i", type = EntryType.PLAN.name, title = "План ${title(i)}", body = body(i * 3), createdAt = now - i * day, updatedAt = now - i * day, tags = someTags())
        }
        for (i in 0 until 200) {
            val start = today - r.nextLong(1, 10 * 365)
            entries += EntryEntity(
                id = "moment$i", type = EntryType.MOMENT.name, title = "Событие $i", momentDay = start,
                momentEnd = if (i % 3 == 0) start + r.nextLong(1, 30) else null, momentGroup = if (i % 2 == 0) "Группа ${i % 6}" else null,
                momentOngoing = i % 17 == 0 && i % 3 != 0, momentRemind = when (i % 5) { 0 -> "year"; 1 -> "month"; else -> null },
                createdAt = now - day, updatedAt = now - day,
            )
        }
        return Generated(entries, people, lists, journal)
    }
}
