package com.sonnik.pin.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.RandomAccessFile
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Прочитанный файл: содержимое и время изменения файла. */
data class Loaded(val data: PinData?, val modified: Long)

/**
 * Pin.zip на компьютере: чтение при каждом изменении файла (телефон синхронизировался) и запись правок.
 *
 * - Перед записью файл перечитывается и сливается с правками (новее — побеждает), чтобы не затереть
 *   то, что телефон успел записать.
 * - Файл перезаписывается на месте, как это делает телефон: Google Диск сохраняет у него тот же идентификатор,
 *   и ссылка на файл в телефоне не ломается. Открываем без обрезки — если файл занят, он остаётся целым.
 * - Google Диск ненадолго занимает файл, пока выгружает или скачивает его. Тогда чтение и запись тихо
 *   повторяются каждые несколько секунд, а несохранённые правки остаются в памяти и не теряются при
 *   перечитывании. Сообщение появляется, только если беда не проходит.
 */
class PinStore(private val scope: CoroutineScope, private val zone: ZoneId = ZoneId.systemDefault()) {
    var path by mutableStateOf(DesktopPrefs.path)
        private set
    var loaded by mutableStateOf<Loaded?>(null)
        private set
    /** Сообщение о затянувшейся беде с файлом; null — всё хорошо. */
    var problem by mutableStateOf<String?>(null)
        private set
    /** Есть правки, ещё не записанные в файл. */
    var unsaved by mutableStateOf(false)
        private set

    private val lock = Mutex()
    private var knownModified = -1L
    /** Правки, которые не удалось записать: данные целиком и снятые отметки журнала. */
    private var pending: PinData? = null
    private var pendingRemoved: Set<String> = emptySet()
    private var readFailures = 0
    private var writeFailures = 0

    fun choose(newPath: String) {
        DesktopPrefs.path = newPath
        path = newPath
        knownModified = -1L
        pending = null
        pendingRemoved = emptySet()
        unsaved = false
        problem = null
        readFailures = 0
        writeFailures = 0
        loaded = null
        scope.launch { tick() }
    }

    /**
     * Начать с чистого листа: пустой Pin.zip по выбранному пути. Если файл уже есть — он просто открывается,
     * ничего не стирается. Телефон потом подключает этот же файл и сливает с ним свои задачи.
     */
    fun create(newPath: String) {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(newPath)
                    if (!file.exists()) {
                        file.parentFile?.mkdirs()
                        // Новый файл — сразу с праздниками России, как у телефона (те же id: телефон их не задвоит).
                        val now = System.currentTimeMillis()
                        val today = com.sonnik.pin.engine.LogicalDay(java.time.Clock.systemDefaultZone(), DesktopPrefs.boundaryMinutes).today()
                        file.writeBytes(Edits.toBytes(Edits.seedHolidays(PinData.EMPTY, today, now), zone))
                    }
                }.exceptionOrNull()
            }
            if (error != null) problem = "Не удалось создать файл: ${error.reason()}" else choose(newPath)
        }
    }

    fun reload() {
        knownModified = -1L
        problem = null
        scope.launch { tick() }
    }

    /** Скрыть сообщение до следующей беды. */
    fun dismissProblem() {
        problem = null
    }

    /** Следить за файлом: вызывать из долгой корутины. */
    suspend fun watch() {
        while (true) {
            tick()
            delay(3_000)
        }
    }

    private suspend fun tick() = lock.withLock {
        if (pending != null) save() else read()
    }

    /** Перечитать, если файл изменился или прошлое чтение не удалось. */
    private suspend fun read() {
        val file = path?.let(::File) ?: return
        val modified = withContext(Dispatchers.IO) { if (file.isFile) file.lastModified() else 0L }
        if (modified == knownModified && readFailures == 0) return
        if (modified == 0L) {
            readFailures++
            if (readFailures >= 3) problem = "Файл не найден: ${file.path}. Если он на Google Диске — проверьте, что Диск запущен."
            return
        }
        val result = withContext(Dispatchers.IO) { runCatching { PinData.read(file.readBytes(), zone) } }
        result.onSuccess { data ->
            knownModified = modified
            readFailures = 0
            problem = null
            loaded = Loaded(data, modified)
        }.onFailure {
            readFailures++
            // Диск часто отдаёт файл недокачанным: молчим полминуты и пробуем снова.
            if (readFailures >= 10) problem = "Не удаётся прочитать файл (${it.reason()}). Повторяю каждые 3 секунды."
        }
    }

    /** Записать отложенные правки, слив их с тем, что сейчас в файле. */
    private suspend fun save() {
        val edited = pending ?: return
        val file = path?.let(::File) ?: return
        val removed = pendingRemoved
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val onDisk = if (file.isFile) PinData.read(file.readBytes(), zone) else PinData.EMPTY
                val merged = Edits.merge(edited, onDisk, removed)
                val bytes = Edits.toBytes(merged, zone)
                // "rw" не обрезает файл при открытии: если он занят, старое содержимое остаётся целым.
                RandomAccessFile(file, "rw").use { raf ->
                    raf.write(bytes)
                    raf.setLength(bytes.size.toLong())
                }
                merged to file.lastModified()
            }
        }
        result.onSuccess { (merged, modified) ->
            pending = null
            pendingRemoved = emptySet()
            unsaved = false
            writeFailures = 0
            knownModified = modified
            problem = null
            loaded = Loaded(merged, modified)
        }.onFailure {
            writeFailures++
            if (writeFailures >= 4) {
                problem = "Не удаётся сохранить в файл (${it.reason()}). Правки не потеряны — сохраню, как только файл освободится."
            }
        }
    }

    /** Применить правку: сразу на экране, затем — в файле (при неудаче — повтор каждые 3 секунды). */
    fun commit(change: (PinData) -> PinData) {
        scope.launch {
            lock.withLock {
                val current = loaded?.data ?: return@withLock
                val edited = change(current)
                if (edited === current) return@withLock
                pendingRemoved = pendingRemoved + (Edits.journalKeys(current) - Edits.journalKeys(edited))
                pending = edited
                unsaved = true
                loaded = loaded?.copy(data = edited)
                save()
            }
        }
    }

    private fun Throwable.reason(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
