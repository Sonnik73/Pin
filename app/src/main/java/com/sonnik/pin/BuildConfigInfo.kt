package com.sonnik.pin

import android.content.Context

/** Версия приложения из манифеста (BuildConfig не включаем ради одной строки). */
object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
}
