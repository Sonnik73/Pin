package com.sonnik.pin.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.Rollover
import com.sonnik.pin.engine.Schedule
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus

// Сущности без Android: этот файл вместе с MdFiles.kt и Archive.kt собирается и в программу для Windows (модуль desktop).
/**
 * Запись (задача, день рождения, план…). Дни — логические эпохальные дни.
 * id — UUID: он же станет id в .md-файле и при синхронизации.
 */
@Entity(tableName = "entries", indices = [Index("dueDay"), Index("status")])
data class EntryEntity(
    @PrimaryKey val id: String,
    /** Имя [EntryType]. */
    val type: String,
    val title: String,
    /** Описание в markdown. */
    val body: String = "",
    /** День, на котором запись стоит сейчас (после переносов). null — без даты. */
    val dueDay: Long? = null,
    /** День, на который запись поставили изначально. */
    val originalDay: Long? = null,
    /** Время в минутах от полуночи; null — без времени. */
    val timeMinutes: Int? = null,
    /** [Priority.level]. */
    val priority: Int = Priority.NORMAL.level,
    /** Теги через запятую, без «#». */
    val tags: String = "",
    val rollovers: Int = 0,
    /** Имя [TaskStatus]. */
    val status: String = TaskStatus.OPEN.name,
    val completedAt: Long? = null,
    /** Логический день выполнения. */
    val completedDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Удаление пометкой — нужно для синхронизации. */
    val deleted: Boolean = false,
    /** Правило повтора ([RepeatRule.format]) для периодических; для них [originalDay] — день текущего повтора. */
    val repeatRule: String? = null,
    /** День, от которого отсчитываются повторы. */
    val repeatAnchor: Long? = null,
    /** Человек (дни рождения, подарки). */
    val personId: String? = null,
    /** Год рождения — для возраста. */
    val birthYear: Int? = null,
    /** За сколько дней напоминать о поводе: «7,1». */
    val remindDays: String? = null,
    /** Подарок: [GiftStatus], цена (как ввели), ссылка, повод (id записи дня рождения или праздника). */
    val giftStatus: String? = null,
    val price: String? = null,
    val link: String? = null,
    val occasionId: String? = null,
    /** План: горизонт ([PlanScope.format]). */
    val planScope: String? = null,
    /** Выключенный праздник: не показывается и не напоминает. */
    val disabled: Boolean = false,
    /** Неизвестные поля из .md-файла (JSON), чтобы записать их обратно без потерь. */
    val extra: String? = null,
    /** Напомнить заранее, минут до времени задачи (первое из напоминаний — для старых версий). */
    val remindBefore: Int = 0,
    /** «Отложить» из уведомления: напомнить не раньше этого момента. На этом устройстве, в файлы не пишется. */
    val snoozeUntil: Long? = null,
    /** Список (проект): «Дом», «Работа»… */
    val listId: String? = null,
    /** Вложения: «id/имя» через перевод строки ([com.sonnik.pin.engine.Attachments]). Файлы — в папке attachments. */
    val attachments: String? = null,
    /**
     * Напоминания: минуты до времени задачи через запятую («0,120,1440»), до пяти; "" — не напоминать;
     * null — запись из старой версии, тогда одно напоминание за [remindBefore].
     */
    val reminders: String? = null,
) {
    val reminderList: List<Int> get() = com.sonnik.pin.engine.Reminders.parse(reminders, remindBefore)

    val rule: RepeatRule? get() = RepeatRule.parse(repeatRule)

    /** Расписание периодической записи; null — запись не повторяется. */
    val schedule: Schedule?
        get() {
            if (entryType !in Rollover.SCHEDULED_TYPES) return null
            val r = rule ?: return null
            return Schedule(r, repeatAnchor ?: originalDay ?: dueDay ?: 0L, rollsOver = entryType == EntryType.RECURRING)
        }

    val entryType: EntryType get() = EntryType.parse(type) ?: EntryType.ONE_TIME
    val entryStatus: TaskStatus get() = TaskStatus.parse(status)
    val entryPriority: Priority get() = Priority.ofLevel(priority)
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun toSnapshot(): TaskSnapshot? {
        val day = dueDay ?: return null
        return TaskSnapshot(
            id = id,
            type = entryType,
            status = entryStatus,
            originalDay = originalDay ?: day,
            currentDay = day,
            timeMinutes = timeMinutes,
            priority = entryPriority,
            rollovers = rollovers,
            createdAt = createdAt,
            title = title,
            reminders = reminderList,
            snoozeUntil = snoozeUntil,
        )
    }
}

/**
 * Журнал событий: выполнения, переносы, пропуски. Из него считается статистика
 * и отдаются данные Крепости; в .md он выгружается помесячно (journal/2026-11.md).
 */
@Entity(tableName = "journal", indices = [Index("day"), Index("entryId"), Index("at")])
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    /** [JournalEvent]. */
    val event: String,
    /** Логический день события: для DONE — день выполнения, для ROLLED и MISSED — день, который не выполнен. */
    val day: Long,
    /** Момент записи события, epoch millis. */
    val at: Long,
    /** Тип записи на момент события. */
    val kind: String,
    val priority: Int,
    /** Сколько раз запись переносилась к моменту события. */
    val rollovers: Int,
    /** Для DONE: выполнено не позже исходного дня. */
    val onTime: Boolean = false,
    /** День повтора (для периодических) или исходный день задачи. */
    val occurrenceDay: Long? = null,
)

object JournalEvent {
    const val DONE = "DONE"
    const val ROLLED = "ROLLED"
    const val MISSED = "MISSED"
    /** Отрезок фокус-таймера; минуты — в поле rollovers. */
    const val FOCUS = "FOCUS"

    /**
     * «Надгробие» снятой отметки: то же событие с минусом («-DONE»), те же запись, день и время.
     * Журнал между устройствами только складывается, поэтому снятие тоже передаётся записью: увидев её,
     * каждое устройство удаляет у себя исходное событие и больше его не принимает.
     */
    fun removed(event: String): String = "-$event"

    fun isRemoved(event: String): Boolean = event.startsWith("-")
}

/** Человек из справочника: к нему привязаны день рождения и подарки. */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.sonnik.pin.engine.Relation]. */
    val relation: String,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Список (проект) записей с цветом. */
@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.sonnik.pin.engine.ListColor]. */
    val color: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Настройки: одна строка с id = 0. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    /** Начало логического дня, минуты от полуночи. */
    val dayBoundaryMinutes: Int = 4 * 60,
    /** Последний день, до которого досчитан перенос. */
    val lastRolloverDay: Long = 0,
    /** Последнее изменение данных — для info.last_changed_at. */
    val lastChangedAt: Long = 0,
    /** Утренняя сводка: включена и во сколько (минуты от полуночи). */
    val morningEnabled: Boolean = true,
    val morningMinutes: Int = 8 * 60,
    /** Последний логический день, за который показана утренняя сводка. */
    val lastMorningDay: Long = 0,
    /** Напоминания по времени задач. */
    val remindersEnabled: Boolean = true,
    /** До какого момента напоминания уже показаны. */
    val lastReminderAt: Long = 0,
    /** Разрешение на уведомления уже спрашивали. */
    val notificationsAsked: Boolean = false,
    /** Синхронизация с Google Диском включена. */
    val syncEnabled: Boolean = false,
    /** Момент начала последней успешной синхронизации. */
    val lastSyncAt: Long = 0,
    /** Ошибка последней синхронизации; null — всё хорошо. */
    val lastSyncError: String? = null,
)
