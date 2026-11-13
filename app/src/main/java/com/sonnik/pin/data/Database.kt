package com.sonnik.pin.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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

    /** День рождения человека (включая удалённый — чтобы воскресить, а не задвоить). */
    @Query("SELECT * FROM entries WHERE personId = :personId AND type = 'BIRTHDAY' LIMIT 1")
    suspend fun birthdayOf(personId: String): EntryEntity?

    /** Всё человека (день рождения, подарки) — включая помеченные удалёнными. */
    @Query("SELECT * FROM entries WHERE personId = :personId")
    suspend fun forPerson(personId: String): List<EntryEntity>

    /** Записи списка (проекта) — включая помеченные удалёнными. */
    @Query("SELECT * FROM entries WHERE listId = :listId")
    suspend fun forList(listId: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE deleted = 0 AND status = 'OPEN' AND dueDay IS NOT NULL AND dueDay < :today")
    suspend fun openBefore(today: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND status = 'OPEN' AND dueDay IS NOT NULL")
    suspend fun openDated(): List<EntryEntity>

    /** Сколько записей выполнено в логический день [day]. */
    @Query("SELECT COUNT(*) FROM entries WHERE deleted = 0 AND completedDay = :day")
    suspend fun doneCount(day: Long): Int

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
interface ListDao {
    @Query("SELECT * FROM lists WHERE deleted = 0 ORDER BY sortOrder, name")
    fun observe(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists")
    suspend fun allWithDeleted(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: String): ListEntity?

    @Upsert
    suspend fun upsert(list: ListEntity)
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

    /** Журнал с дня [from] включительно: «Итогам» больше не нужно, а весь журнал растёт бесконечно. */
    @Query("SELECT * FROM journal WHERE day >= :from ORDER BY at")
    fun observeSince(from: Long): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE kind = 'HABIT' AND event = 'DONE' AND day >= :from")
    fun observeHabitDoneSince(from: Long): Flow<List<JournalEntity>>

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
    entities = [EntryEntity::class, JournalEntity::class, SettingsEntity::class, PersonEntity::class, ListEntity::class],
    version = 15,
    exportSchema = true,
)
abstract class PinDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun journal(): JournalDao
    abstract fun settings(): SettingsDao
    abstract fun people(): PersonDao
    abstract fun lists(): ListDao

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

        /** 0.24: списки (проекты). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN listId TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /** 0.52: вложения к задачам. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN attachments TEXT")
            }
        }

        /** 0.71: несколько напоминаний у задачи. Старые записи (null) напоминают, как раньше, — за remindBefore. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN reminders TEXT")
            }
        }

        /** 0.72: важные даты и периоды. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN momentDay INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN momentEnd INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN momentGroup TEXT")
            }
        }

        /** 0.75: период важной даты «по настоящее время». */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN momentOngoing INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 0.77: «Напоминать каждый…» у важной даты. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN momentRemind TEXT")
            }
        }

        /** 0.85: отпечаток Pin.zip — синхронизация без изменений не разбирает файл заново. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN fileSyncHash TEXT")
            }
        }

        /** 0.87: индексы под частые запросы — «Сегодня»/календарь, люди, счётчик выполненных. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_deleted_dueDay` ON `entries` (`deleted`, `dueDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_personId` ON `entries` (`personId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_completedDay` ON `entries` (`completedDay`)")
            }
        }

        /** 0.91: размер и время документа Pin.zip — быстрая сверка без чтения файла. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN fileSyncStat TEXT")
            }
        }

        val MIGRATIONS = arrayOf<Migration>(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
        )

        fun create(context: Context): PinDatabase =
            Room.databaseBuilder(context, PinDatabase::class.java, "pin.db").addMigrations(*MIGRATIONS).build()
    }
}
