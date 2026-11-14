package com.sonnik.pin.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Значок следует за темой: включён ровно один алиас, у каждой палитры — свой. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun enabled(alias: String): Boolean =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, "com.sonnik.pin.Launcher$alias")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    @Test
    fun `смена палитры включает её алиас и выключает остальные`() {
        LauncherIcon.apply(context, ThemePalette.CYBER)
        assertTrue(enabled("Cyber"))
        assertEquals(false, enabled("Pin"))
        assertEquals(false, enabled("Steel"))

        LauncherIcon.apply(context, ThemePalette.GARRISON)
        assertTrue(enabled("Garrison"))
        assertEquals(false, enabled("Cyber"))

        LauncherIcon.apply(context, ThemePalette.PIN)
        assertTrue(enabled("Pin"))
        assertEquals(false, enabled("Garrison"))

        // «Обои» значку достаётся «Сталь».
        LauncherIcon.apply(context, ThemePalette.DYNAMIC)
        assertTrue(enabled("Steel"))
        assertEquals(false, enabled("Pin"))
    }
}
