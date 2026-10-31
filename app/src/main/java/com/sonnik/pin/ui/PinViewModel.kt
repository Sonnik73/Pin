package com.sonnik.pin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonnik.pin.PinApp
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.TodayBoard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Всё, что нужно экрану «Сегодня». */
data class TodayUi(
    val today: Long,
    val board: TodayBoard,
    val entries: Map<String, EntryEntity>,
    val dayBoundaryMinutes: Int,
)

class PinViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as PinApp).repository
    private val today = MutableStateFlow<Long?>(null)
    private var boundaryJob: Job? = null

    val ui: StateFlow<TodayUi?> = combine(repo.datedEntries, today.filterNotNull(), repo.settings) { list, day, settings ->
        val byId = list.associateBy { it.id }
        TodayUi(
            today = day,
            board = TodayBoard.build(list.mapNotNull { it.toSnapshot() }, day, doneDayOf = { byId[it.id]?.completedDay }),
            entries = byId,
            dayBoundaryMinutes = settings.dayBoundaryMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    /** Досчитать перенос и обновить «сегодня»; заодно взвести обновление на следующую границу дня. */
    fun refresh() {
        viewModelScope.launch {
            repo.rollover()
            val day = repo.logicalDay()
            today.value = day.today()
            boundaryJob?.cancel()
            boundaryJob = viewModelScope.launch {
                delay((day.nextBoundary() - System.currentTimeMillis()).coerceAtLeast(1_000L) + 1_000L)
                refresh()
            }
        }
    }

    fun save(id: String?, draft: TaskDraft) {
        viewModelScope.launch {
            if (id == null) repo.create(draft) else repo.update(id, draft)
            repo.rollover()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun setDone(id: String, done: Boolean) {
        viewModelScope.launch {
            repo.setDone(id, done)
            if (!done) repo.rollover()
        }
    }

    fun setDayBoundary(minutes: Int) {
        viewModelScope.launch {
            repo.setDayBoundary(minutes)
            refresh()
        }
    }
}
