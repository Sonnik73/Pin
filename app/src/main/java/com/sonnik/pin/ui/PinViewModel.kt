package com.sonnik.pin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonnik.pin.PinApp
import android.net.Uri
import com.sonnik.pin.data.Archive
import com.sonnik.pin.data.EntryEntity
import com.sonnik.pin.data.JournalEntity
import com.sonnik.pin.data.PersonEntity
import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.data.SettingsEntity
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.FocusState
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.HabitInfo
import com.sonnik.pin.engine.RepeatRule
import com.sonnik.pin.engine.StatEvent
import com.sonnik.pin.sync.SyncWorker
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.PlanScope
import com.sonnik.pin.engine.Relation
import com.sonnik.pin.data.TaskDraft
import com.sonnik.pin.engine.TaskStatus
import com.sonnik.pin.engine.TodayBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.sonnik.pin.data.ListEntity
import com.sonnik.pin.engine.ListColor
import com.sonnik.pin.engine.QuickDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Всё, что нужно экранам. */
data class PinUi(
    val today: Long,
    val board: TodayBoard,
    /** Все неудалённые записи, включая подарки, планы и праздники. */
    val entries: Map<String, EntryEntity>,
    val settings: SettingsEntity,
    val people: List<PersonEntity> = emptyList(),
    /** События дня: праздники и напоминания о ближайших поводах. */
    val events: List<String> = emptyList(),
    /** Привычки с отметками. */
    val habits: List<HabitInfo> = emptyList(),
    /** Списки (проекты). */
    val lists: List<ListEntity> = emptyList(),
) {
    val listsById: Map<String, ListEntity> by lazy { lists.associateBy { it.id } }

    val dayBoundaryMinutes: Int get() = settings.dayBoundaryMinutes

    /** Все теги записей — для подсказок при вводе. */
    val allTags: List<String> by lazy { entries.values.flatMap { it.tagList }.distinct().sorted() }
}

class PinViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as PinApp).repository
    private val today = MutableStateFlow<Long?>(null)
    private var boundaryJob: Job? = null

    val ui: StateFlow<PinUi?> = combine(repo.allEntries, today.filterNotNull(), repo.settings, combine(repo.people, repo.lists) { p, l -> p to l }, repo.habitDone) { all, day, settings, (people, lists), habitDone ->
        val list = all.filter { it.dueDay != null && !it.disabled }
        val byId = all.associateBy { it.id }
        (getApplication<Application>() as PinApp).titles = byId.mapValues { it.value.title }
        val snapshots = list.mapNotNull { it.toSnapshot() }
        // Периодическая задача, выполненная сегодня, уже стоит на следующем повторе — показываем и её сегодняшнее выполнение.
        val doneRecurring = list
            .filter { it.schedule != null && it.completedDay == day }
            .mapNotNull { e -> e.toSnapshot()?.copy(status = TaskStatus.DONE, currentDay = day, originalDay = day, rollovers = 0) }
        PinUi(
            today = day,
            board = TodayBoard.build(snapshots + doneRecurring, day, doneDayOf = { byId[it.id]?.completedDay }),
            entries = byId,
            settings = settings,
            people = people,
            events = PinRepository.occasionNotices(all, people, day).let { (holidays, notices) ->
                holidays + notices.map(Occasions::describe)
            },
            lists = lists,
            habits = all.filter { it.entryType == EntryType.HABIT }.sortedBy { it.createdAt }.map { e ->
                HabitInfo(e.id, e.rule, e.repeatAnchor ?: day, habitDone[e.id].orEmpty())
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val updates = (app as PinApp).updates

    /** Найденное обновление — окно «Доступна новая версия». */
    val update = MutableStateFlow<com.sonnik.pin.engine.ReleaseInfo?>(null)

    init {
        viewModelScope.launch {
            repo.seedHolidays()
            repo.dedupeJournal()
        }
        refresh()
        viewModelScope.launch { update.value = updates.checkIfDue() }
    }

    /** Ручная проверка: «Проверяем…», «Обновлений нет», «Не удалось» (новая версия — в [update]). */
    val updateCheck = MutableStateFlow<UpdateCheckState?>(null)

    fun checkUpdatesNow() {
        updateCheck.value = UpdateCheckState.Checking
        viewModelScope.launch {
            when (val r = updates.checkNow()) {
                is com.sonnik.pin.data.UpdateChecker.Result.Available -> {
                    updateCheck.value = null
                    update.value = r.release
                }
                is com.sonnik.pin.data.UpdateChecker.Result.UpToDate ->
                    updateCheck.value = UpdateCheckState.UpToDate(updates.currentVersion, r.latest)
                is com.sonnik.pin.data.UpdateChecker.Result.Failed -> updateCheck.value = UpdateCheckState.Failed(r.reason)
            }
        }
    }

    val currentVersion: String get() = updates.currentVersion

    fun downloadUpdate(release: com.sonnik.pin.engine.ReleaseInfo) {
        updates.download(release)
        update.value = null
    }

    fun skipUpdate(release: com.sonnik.pin.engine.ReleaseInfo) {
        updates.skip(release)
        update.value = null
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

    /** Журнал для статистики. */
    val statEvents: StateFlow<List<StatEvent>> = repo.journalAll.map { list ->
        list.map { StatEvent(it.entryId, it.event, it.day, EntryType.parse(it.kind) ?: EntryType.ONE_TIME, it.priority, it.rollovers, it.onTime) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Выполнения за отрезок дней — для календаря. */
    fun doneBetween(from: Long, to: Long): Flow<List<JournalEntity>> = repo.doneBetween(from, to)

    fun save(id: String?, draft: TaskDraft) {
        viewModelScope.launch {
            if (id == null) repo.create(draft) else repo.update(id, draft)
            repo.rollover()
        }
    }

    /** Быстрое добавление: «@дом» кладёт в список (создаёт, если такого нет). */
    fun quickAdd(d: QuickDraft) {
        viewModelScope.launch {
            val listId = d.list?.let { repo.listIdFor(it) }
            repo.create(d.toTaskDraft().copy(listId = listId))
            repo.rollover()
        }
    }

    /** План импорта людей из файла — окно с выбором. */
    val peopleImport = MutableStateFlow<List<Pair<com.sonnik.pin.engine.SharedPerson, com.sonnik.pin.engine.ImportAction>>?>(null)

    /** Прочитать .vcf и показать, кого можно добавить. */
    fun loadPeopleFile(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            val people = text?.let(com.sonnik.pin.engine.VCard::parse).orEmpty()
            if (people.isEmpty()) {
                message.value = "В файле не нашлось людей. Нужен файл .vcf — его делает «Поделиться людьми» в Pin или «Контакты»."
            } else {
                peopleImport.value = com.sonnik.pin.engine.PeopleImport.plan(repo.peopleBirthdays(), people)
            }
        }
    }

    fun importPeople(selected: List<com.sonnik.pin.engine.SharedPerson>) {
        peopleImport.value = null
        viewModelScope.launch {
            val n = repo.importPeople(selected)
            message.value = if (n == 0) "Никого нового — все уже есть." else "Добавлено: $n."
        }
    }

    /** Записать выбранных людей в .vcf и открыть «Поделиться» (мессенджер, почта, Диск). */
    fun sharePeople(ids: Collection<String>) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val people = repo.sharedPeople(ids)
            if (people.isEmpty()) return@launch
            val file = withContext(Dispatchers.IO) {
                val dir = java.io.File(app.cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                java.io.File(dir, if (people.size == 1) "${people.single().name}.vcf" else "Pin — люди.vcf")
                    .apply { writeText(com.sonnik.pin.engine.VCard.write(people)) }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "com.sonnik.pin.files", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/x-vcard")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Дни рождения из Pin")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = android.content.Intent.createChooser(send, "Поделиться людьми")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(chooser) }
        }
    }

    /** Перерисовать виджеты (после смены прозрачности). */
    fun refreshWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            runCatching { com.sonnik.pin.widget.TodayWidget.refresh(app, repo) }
            runCatching { com.sonnik.pin.widget.CalendarWidget.refresh(app, repo) }
        }
    }

    /** Пересчитать будильник уведомлений (например, после включения или выключения привычек). */
    fun rescheduleNotifications() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.sonnik.pin.notify.Notifier.deliver(getApplication(), repo) } }
    }

    fun setBody(id: String, body: String) {
        viewModelScope.launch { repo.setBody(id, body) }
    }

    fun setImportant(id: String, important: Boolean) {
        viewModelScope.launch { repo.setPriority(id, if (important) com.sonnik.pin.engine.Priority.HIGH else com.sonnik.pin.engine.Priority.NORMAL) }
    }

    fun saveList(id: String?, name: String, color: ListColor) {
        viewModelScope.launch { repo.saveList(id, name, color) }
    }

    fun deleteList(id: String) {
        viewModelScope.launch { repo.deleteList(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun setDone(id: String, done: Boolean) {
        viewModelScope.launch {
            repo.setDone(id, done)
            undoDone.value = if (done) ui.value?.entries?.get(id)?.let { id to it.title } else null
        }
    }

    /** Только что отмеченная задача — для «Отменить» внизу экрана. */
    val undoDone = MutableStateFlow<Pair<String, String>?>(null)

    fun setDayBoundary(minutes: Int) {
        viewModelScope.launch {
            repo.setDayBoundary(minutes)
            refresh()
        }
    }

    fun savePerson(id: String?, name: String, relation: String, note: String, birthday: PinRepository.Birthday?, remindDays: List<Int>) {
        viewModelScope.launch { repo.savePerson(id, name, relation, note, birthday, remindDays) }
    }

    fun deletePerson(id: String) {
        viewModelScope.launch { repo.deletePerson(id) }
    }

    fun saveGift(id: String?, draft: PinRepository.GiftDraft) {
        viewModelScope.launch { repo.saveGift(id, draft) }
    }

    fun setGiftStatus(id: String, status: GiftStatus) {
        viewModelScope.launch { repo.setGiftStatus(id, status) }
    }

    fun savePlan(id: String?, title: String, body: String, scope: PlanScope, tags: List<String>) {
        viewModelScope.launch { repo.savePlan(id, title, body, scope, tags) }
    }

    fun planToTask(id: String, day: Long, time: Int?, title: String, body: String, scope: PlanScope, tags: List<String>) {
        viewModelScope.launch {
            repo.savePlan(id, title, body, scope, tags)
            repo.planToTask(id, day, time)
        }
    }

    fun saveHoliday(id: String?, title: String, month: Int, day: Int) {
        viewModelScope.launch { repo.saveHoliday(id, title, month, day, listOf(1)) }
    }

    fun setDisabled(id: String, disabled: Boolean) {
        viewModelScope.launch { repo.setDisabled(id, disabled) }
    }

    /** Сообщение для пользователя (итог экспорта, импорта, синхронизации). */
    val message = MutableStateFlow<String?>(null)

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            message.value = runCatching {
                val files = repo.exportFiles()
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
                    out.use { Archive.write(it, files) }
                }
                "Сохранено файлов: ${files.size}."
            }.getOrElse { "Экспорт не удался: ${it.message}" }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            message.value = runCatching {
                val files = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
                    input.use { Archive.read(it) }
                }
                if (files.isEmpty()) error("в архиве нет .md-файлов")
                repo.importFiles(files).describe() + "."
            }.getOrElse { "Импорт не удался: ${it.message}" }
        }
    }

    fun driveConnected() {
        viewModelScope.launch {
            repo.updateSettings { it.copy(syncEnabled = true, lastSyncError = null) }
            SyncWorker.schedule(getApplication())
            SyncWorker.requestSoon(getApplication(), delaySeconds = 0)
            message.value = "Google Диск подключён. Первая синхронизация уже идёт — папка «Pin» появится на Диске."
        }
    }

    fun driveDisconnect() {
        viewModelScope.launch {
            repo.updateSettings { it.copy(syncEnabled = false, lastSyncError = null) }
            SyncWorker.cancel(getApplication())
        }
    }

    fun syncNow() {
        SyncWorker.requestSoon(getApplication(), delaySeconds = 0)
        message.value = "Синхронизация запущена."
    }

    fun review(id: String, action: ReviewAction) {
        viewModelScope.launch {
            when (action) {
                is ReviewAction.Keep -> repo.reschedule(id, action.day)
                ReviewAction.Done -> repo.setDone(id, true)
                ReviewAction.ToPlans -> repo.moveToPlans(id)
                ReviewAction.SkipOccurrence -> repo.skipOccurrence(id)
                ReviewAction.Delete -> repo.delete(id)
            }
        }
    }

    /** День, который надо открыть в календаре (нажатие на день в виджете). */
    val calendarJump = MutableStateFlow<Long?>(null)

    /** Открыть экран по просьбе извне (кнопка в уведомлении): «review» — разбор дня. */
    val pendingScreen = MutableStateFlow<String?>(null)

    fun saveHabit(id: String?, title: String, rule: RepeatRule?, remindAt: Int?) {
        viewModelScope.launch { repo.saveHabit(id, title, rule, remindAt) }
    }

    fun toggleHabit(id: String, day: Long) {
        viewModelScope.launch { repo.toggleHabit(id, day) }
    }

    /** Фокус-таймер. */
    val focus: StateFlow<FocusState?> = (app as PinApp).focus.state
    /** Задача, для которой открыт экран фокуса. */
    val focusTask = MutableStateFlow<String?>(null)

    fun startFocus(taskId: String?) = (getApplication<Application>() as PinApp).focus.start(taskId)
    fun stopFocus() = (getApplication<Application>() as PinApp).focus.stop()

    fun updateSettings(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { repo.updateSettings(change) }
    }
}
