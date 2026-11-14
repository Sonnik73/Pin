package com.sonnik.pin.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Значок на рабочем столе меняется вместе с темой: в манифесте по алиасу на каждую палитру,
 * включён ровно один. При смене палитры включаем новый алиас и выключаем остальные.
 * Лаунчер перерисовывает значок за пару секунд; сами данные и запуски приложения не затронуты.
 */
object LauncherIcon {
    /** Алиасы из манифеста: суффикс компонента. Имена не переименовывать — синхронно с манифестом. */
    val ALL = listOf("Pin", "Steel", "Forest", "Sunset", "Amethyst", "Graphite", "Forge", "Garrison", "Cyber")

    /** Палитра → алиас; «Обои» перекрашивают интерфейс с обоев, значку достаётся «Сталь». */
    private val OF_PALETTE = mapOf(
        ThemePalette.PIN to "Pin",
        ThemePalette.STEEL to "Steel",
        ThemePalette.FOREST to "Forest",
        ThemePalette.SUNSET to "Sunset",
        ThemePalette.AMETHYST to "Amethyst",
        ThemePalette.GRAPHITE to "Graphite",
        ThemePalette.FORGE to "Forge",
        ThemePalette.GARRISON to "Garrison",
        ThemePalette.CYBER to "Cyber",
        ThemePalette.DYNAMIC to "Steel",
    )

    private fun component(context: Context, alias: String) = ComponentName(context, "com.sonnik.pin.Launcher$alias")

    private fun enabled(pm: PackageManager, context: Context, alias: String): Boolean {
        val state = pm.getComponentEnabledSetting(component(context, alias))
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> alias == "Pin" // как в манифесте: по умолчанию включён «Pin»
        }
    }

    /** Включить значок палитры [palette] и выключить остальные. Безопасно вызывать повторно. */
    fun apply(context: Context, palette: ThemePalette) {
        val target = OF_PALETTE[palette] ?: "Pin"
        val pm = context.packageManager
        runCatching {
            if (!enabled(pm, context, target)) {
                // Сначала включаем новый, потом выключаем старые — рабочий стол без значка не остаётся.
                pm.setComponentEnabledSetting(
                    component(context, target),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            ALL.forEach { alias ->
                if (alias != target && enabled(pm, context, alias)) {
                    pm.setComponentEnabledSetting(
                        component(context, alias),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }
}
