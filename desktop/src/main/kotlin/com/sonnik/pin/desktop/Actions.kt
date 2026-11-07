package com.sonnik.pin.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.engine.QuickDraft
import com.sonnik.pin.engine.SearchState

/** Что открыть в окне правки: [id] = null — новая задача на [day]. */
data class EditTarget(val id: String?, val day: Long)

/** Действия с задачами из любых экранов; по умолчанию ничего не делают (снимки экранов). */
class Actions(
    val toggle: (EntryEntity, Boolean) -> Unit = { _, _ -> },
    val open: (EntryEntity) -> Unit = {},
    val add: (Long) -> Unit = {},
    val quickAdd: (QuickDraft) -> Unit = {},
    /** Перенести на день (на завтра, на дату). */
    val reschedule: (EntryEntity, Long) -> Unit = { _, _ -> },
    val toPlans: (EntryEntity) -> Unit = {},
    val skip: (EntryEntity) -> Unit = {},
    val delete: (EntryEntity) -> Unit = {},
    /** Отметить пункт «- [ ]» в описании прямо в строке задачи. */
    val setBody: (EntryEntity, String) -> Unit = { _, _ -> },
    val toggleHabit: (String, Long) -> Unit = { _, _ -> },
    /** Открыть разбор перенесённых. */
    val review: () -> Unit = {},
    /** Перейти в поиск с нужным состоянием (архив — «Выполнено»). */
    val search: (SearchState) -> Unit = {},
    /** Запустить фокус-таймер по задаче. */
    val focus: (EntryEntity) -> Unit = {},
)

val LocalActions = staticCompositionLocalOf { Actions() }
