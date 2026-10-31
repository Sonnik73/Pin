package com.sonnik.pin.ecosystem

import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sonnik.ecosystem.contract.EcosystemContract.Tasks
import com.sonnik.pin.PinApp
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.Priority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinProviderTest {
    private val app: PinApp = ApplicationProvider.getApplicationContext()
    private val provider = Robolectric.buildContentProvider(PinProvider::class.java).create(Tasks.AUTHORITY).get()

    private fun query(uri: String, projection: Array<String>? = null): Cursor = provider.query(Uri.parse(uri), projection, null, null, null)

    private fun Cursor.rows(): List<Map<String, String?>> = use { c ->
        buildList {
            while (c.moveToNext()) add(c.columnNames.associateWith { c.getString(c.getColumnIndexOrThrow(it)) })
        }
    }

    @Test
    fun `info, сводки дней и выполнения`() = runBlocking {
        val repo = app.repository
        val today = repo.logicalDay().today()
        val a = repo.create(TaskDraft("Секретная задача", day = today, priority = Priority.HIGH))
        repo.create(TaskDraft("Ещё одна", day = today))
        repo.setDone(a, true)

        val info = query(Tasks.Info.URI).rows().single()
        assertEquals("1", info[Tasks.Info.SCHEMA_VERSION])
        assertEquals("240", info[Tasks.Info.DAY_BOUNDARY_MINUTES])
        assertTrue(info.getValue(Tasks.Info.LAST_CHANGED_AT)!!.toLong() > 0)

        val days = query("${Tasks.DayStats.URI}?from=${today - 2}&to=$today").rows()
        assertEquals(listOf(today - 2, today - 1, today).map(Long::toString), days.map { it[Tasks.DayStats.DAY] })
        val last = days.last()
        assertEquals("2", last[Tasks.DayStats.PLANNED])
        assertEquals("1", last[Tasks.DayStats.DONE])
        assertEquals("1", last[Tasks.DayStats.DONE_ON_TIME])
        assertEquals("1", last[Tasks.DayStats.IMPORTANT_DONE])
        assertEquals(7, query(Tasks.DayStats.URI).rows().size)

        val done = query("${Tasks.Completions.URI}?since=0").rows().single()
        assertEquals(Tasks.Completions.KIND_ONE_TIME, done[Tasks.Completions.KIND])
        assertEquals("1", done[Tasks.Completions.ON_TIME])
        assertEquals("2", done[Tasks.Completions.PRIORITY])
        // Никаких названий наружу.
        assertTrue(done.values.none { it?.contains("Секретная") == true })

        val projected = query(Tasks.DayStats.URI, arrayOf(Tasks.DayStats.DONE, Tasks.DayStats.DAY))
        assertEquals(listOf(Tasks.DayStats.DONE, Tasks.DayStats.DAY), projected.columnNames.toList())
        projected.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `неизвестный путь`() {
        query(Tasks.BASE_URI + "secrets")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `неизвестная колонка`() {
        query(Tasks.Info.URI, arrayOf("title"))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `запись запрещена`() {
        provider.delete(Uri.parse(Tasks.Info.URI), null, null)
    }
}
