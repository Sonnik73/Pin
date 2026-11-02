package com.sonnik.pin.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.QuickDraft

/** Что открыть в окне правки: [id] = null — новая задача на [day]. */
data class EditTarget(val id: String?, val day: Long)

/** Действия с задачами из любых экранов; по умолчанию ничего не делают (снимки экранов). */
class Actions(
    val toggle: (EntryEntity, Boolean) -> Unit = { _, _ -> },
    val open: (EntryEntity) -> Unit = {},
    val add: (Long) -> Unit = {},
    val quickAdd: (QuickDraft) -> Unit = {},
)

val LocalActions = staticCompositionLocalOf { Actions() }
