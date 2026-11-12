package com.sonnik.pin.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** База версии 0.1 открывается в новой версии без потери данных. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Схема 0.1 — ровно так её создавал Room. */
    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `entries` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
            "`dueDay` INTEGER, `originalDay` INTEGER, `timeMinutes` INTEGER, `priority` INTEGER NOT NULL, `tags` TEXT NOT NULL, " +
            "`rollovers` INTEGER NOT NULL, `status` TEXT NOT NULL, `completedAt` INTEGER, `completedDay` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_entries_dueDay` ON `entries` (`dueDay`)",
        "CREATE INDEX IF NOT EXISTS `index_entries_status` ON `entries` (`status`)",
        "CREATE TABLE IF NOT EXISTS `journal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` TEXT NOT NULL, " +
            "`event` TEXT NOT NULL, `day` INTEGER NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `priority` INTEGER NOT NULL, " +
            "`rollovers` INTEGER NOT NULL, `onTime` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_journal_day` ON `journal` (`day`)",
        "CREATE INDEX IF NOT EXISTS `index_journal_entryId` ON `journal` (`entryId`)",
        "CREATE INDEX IF NOT EXISTS `index_journal_at` ON `journal` (`at`)",
        "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `dayBoundaryMinutes` INTEGER NOT NULL, " +
            "`lastRolloverDay` INTEGER NOT NULL, `lastChangedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )

    @Test
    fun `миграция 1 → 13`() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            v1.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO entries VALUES ('a', 'ONE_TIME', 'Купить хлеб', '', 100, 99, NULL, 2, 'дом', 1, 'OPEN', NULL, NULL, 1, 2, 0)",
            )
            db.execSQL("INSERT INTO journal (entryId, event, day, at, kind, priority, rollovers, onTime) VALUES ('a', 'ROLLED', 99, 5, 'ONE_TIME', 2, 1, 0)")
            db.execSQL("INSERT INTO settings VALUES (0, 180, 100, 7)")
            db.version = 1
        }
        val room = Room.databaseBuilder(context, PinDatabase::class.java, name)
            .addMigrations(*PinDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val entry = room.entries().get("a")!!
            assertEquals("Купить хлеб", entry.title)
            assertEquals(1, entry.rollovers)
            assertNull(entry.repeatRule)
            val settings = room.settings().get()!!
            assertEquals(180, settings.dayBoundaryMinutes)
            assertTrue(settings.morningEnabled)
            assertEquals(480, settings.morningMinutes)
            assertNull(room.journal().between(99, 99).single().occurrenceDay)
            assertTrue(room.people().allWithDeleted().isEmpty())
            assertEquals(false, entry.disabled)
            assertNull(entry.listId)
            assertNull(entry.attachments)
            // Старая задача напоминает, как раньше: одно напоминание за remindBefore.
            assertNull(entry.reminders)
            assertEquals(listOf(0), entry.reminderList)
            assertNull(entry.momentDay)
            assertNull(entry.momentEnd)
            assertNull(entry.momentGroup)
            assertEquals(false, entry.momentOngoing)
            assertNull(entry.momentRemind)
            assertNull(settings.fileSyncHash)
            assertTrue(room.lists().allWithDeleted().isEmpty())
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
