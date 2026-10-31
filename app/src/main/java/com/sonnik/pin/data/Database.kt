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
) {
    val rule: RepeatRule? get() = RepeatRule.parse(repeatRule)

    /** Расписание периодической записи; null — запись не повторяется. */
    val schedule: Schedule?
        get() {
            if (entryType != EntryType.RECURRING) return null
            val r = rule ?: return null
            return Schedule(r, repeatAnchor ?: originalDay ?: dueDay ?: 0L)
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
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE deleted = 0 AND dueDay IS NOT NULL")
    fun observeDated(): Flow<List<EntryEntity>>

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
interface JournalDao {
    @Insert
    suspend fun insert(event: JournalEntity): Long

    @Insert
    suspend fun insertAll(events: List<JournalEntity>)

    @Query("DELETE FROM journal WHERE entryId = :entryId AND event = :event AND day = :day")
    suspend fun delete(entryId: String, event: String, day: Long)

    @Query("SELECT * FROM journal WHERE day BETWEEN :from AND :to ORDER BY at")
    suspend fun between(from: Long, to: Long): List<JournalEntity>

    @Query("SELECT * FROM journal WHERE entryId = :entryId AND event = 'DONE' ORDER BY at DESC LIMIT 1")
    suspend fun lastDone(entryId: String): JournalEntity?

    @Query("DELETE FROM journal WHERE id = :id")
    suspend fun deleteById(id: Long)

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
    entities = [EntryEntity::class, JournalEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PinDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun journal(): JournalDao
    abstract fun settings(): SettingsDao

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

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)

        fun create(context: Context): PinDatabase =
            Room.databaseBuilder(context, PinDatabase::class.java, "pin.db").addMigrations(*MIGRATIONS).build()
    }
}
