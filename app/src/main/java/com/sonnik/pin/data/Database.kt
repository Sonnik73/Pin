package com.sonnik.pin.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.Priority
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.Rollover
import com.sonnik.pin.engine.Schedule
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.TaskStatus
import kotlinx.coroutines.flow.Flow

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
    /** Напомнить заранее, минут до времени задачи. */
    val remindBefore: Int = 0,
    /** «Отложить» из уведомления: напомнить не раньше этого момента. На этом устройстве, в файлы не пишется. */
    val snoozeUntil: Long? = null,
) {
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
            remindBefore = remindBefore,
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

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE deleted = 0 AND dueDay IS NOT NULL")
    fun observeDated(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deleted = 0")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deleted = 0")
    suspend fun all(): List<EntryEntity>

    @Query("SELECT * FROM entries")
    suspend fun allWithDeleted(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE deleted = 0 AND status = 'OPEN' AND dueDay IS NOT NULL AND dueDay < :today")
    suspend fun openBefore(today: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND status = 'OPEN' AND dueDay IS NOT NULL")
    suspend fun openDated(): List<EntryEntity>

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people WHERE deleted = 0 ORDER BY name")
    fun observe(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people")
    suspend fun allWithDeleted(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: String): PersonEntity?

    @Upsert
    suspend fun upsert(person: PersonEntity)
}

@Dao
interface JournalDao {
    @Insert
    suspend fun insert(event: JournalEntity): Long

    @Insert
    suspend fun insertAll(events: List<JournalEntity>)

    @Query("DELETE FROM journal WHERE entryId = :entryId AND event = :event AND day = :day")
    suspend fun delete(entryId: String, event: String, day: Long)

    @Query("SELECT * FROM journal ORDER BY at")
    suspend fun all(): List<JournalEntity>

    @Query("SELECT * FROM journal ORDER BY at")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE kind = 'HABIT' AND event = 'DONE'")
    fun observeHabitDone(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE entryId = :entryId AND kind = 'HABIT' AND event = 'DONE' AND day = :day LIMIT 1")
    suspend fun habitDoneOn(entryId: String, day: Long): JournalEntity?

    @Query("SELECT * FROM journal WHERE kind = 'HABIT' AND event = 'DONE' AND day BETWEEN :from AND :to")
    suspend fun habitDoneBetween(from: Long, to: Long): List<JournalEntity>

    @Query("SELECT * FROM journal WHERE day BETWEEN :from AND :to ORDER BY at")
    suspend fun between(from: Long, to: Long): List<JournalEntity>

    @Query("SELECT * FROM journal WHERE entryId = :entryId AND event = 'DONE' ORDER BY at DESC LIMIT 1")
    suspend fun lastDone(entryId: String): JournalEntity?

    @Upsert
    suspend fun upsertAll(events: List<JournalEntity>)

    @Query("DELETE FROM journal WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM journal WHERE event = 'DONE' AND at >= :since ORDER BY at, id")
    suspend fun doneSince(since: Long): List<JournalEntity>

    @Query("SELECT * FROM journal WHERE event = 'DONE' AND day BETWEEN :from AND :to")
    fun observeDone(from: Long, to: Long): Flow<List<JournalEntity>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(settings: SettingsEntity)
}

@Database(
    entities = [EntryEntity::class, JournalEntity::class, SettingsEntity::class, PersonEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class PinDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun journal(): JournalDao
    abstract fun settings(): SettingsDao
    abstract fun people(): PersonDao

    companion object {
        /** 0.2: периодические задачи и настройки уведомлений. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN repeatRule TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN repeatAnchor INTEGER")
                db.execSQL("ALTER TABLE journal ADD COLUMN occurrenceDay INTEGER")
                db.execSQL("ALTER TABLE settings ADD COLUMN morningEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN morningMinutes INTEGER NOT NULL DEFAULT 480")
                db.execSQL("ALTER TABLE settings ADD COLUMN lastMorningDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN remindersEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN lastReminderAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN notificationsAsked INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 0.3: люди, дни рождения, праздники, подарки, планы. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("personId TEXT", "birthYear INTEGER", "remindDays TEXT", "giftStatus TEXT", "price TEXT", "link TEXT",
                    "occasionId TEXT", "planScope TEXT", "disabled INTEGER NOT NULL DEFAULT 0")
                    .forEach { db.execSQL("ALTER TABLE entries ADD COLUMN $it") }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `relation` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /** 0.4: неизвестные поля .md-файлов. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN extra TEXT")
            }
        }

        /** 0.9: синхронизация с Google Диском. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN lastSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN lastSyncError TEXT")
            }
        }

        /** 0.20: напоминание заранее и «отложить». */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN remindBefore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN snoozeUntil INTEGER")
            }
        }

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun create(context: Context): PinDatabase =
            Room.databaseBuilder(context, PinDatabase::class.java, "pin.db").addMigrations(*MIGRATIONS).build()
    }
}
