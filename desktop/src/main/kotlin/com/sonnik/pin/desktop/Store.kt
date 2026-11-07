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
            // Несохранённое — пробуем чаще: второй компьютер обычно отпускает файл за доли секунды.
            delay(if (pending != null) 1_000 else 3_000)
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
        val result = withContext(Dispatchers.IO) { runCatching { PinData.read(PinFileIo.readLocked(file), zone) } }
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
                val merged = PinFileIo.update(file) { current ->
                    val onDisk = if (current.isEmpty()) PinData.EMPTY else PinData.read(current, zone)
                    val merged = Edits.merge(edited, onDisk, removed)
                    merged to Edits.toBytes(merged, zone)
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
            if (writeFailures >= 10) {
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

/** Файл занят другим компьютером — повторим чуть позже, правки ждут в памяти. */
class FileBusyException : java.io.IOException("файл сейчас сохраняет другой компьютер")

/**
 * Чтение и запись Pin.zip под блокировкой Windows — чтобы два компьютера в сети (или компьютер и Google Диск)
 * не писали в файл одновременно.
 *
 * - Запись: исключительная блокировка на всё время «прочитать → слить → записать → проверить». Пока она держится,
 *   второй компьютер не может ни читать, ни писать файл и тихо повторяет через секунду. Поэтому чужая правка,
 *   записанная между нашим чтением и записью, не потеряется: такого промежутка просто нет.
 * - Чтение: общая блокировка — читать могут многие, но не во время чужой записи (не получим полфайла).
 * - Файл открывается без обрезки ("rw"), так что занятый или недоступный файл остаётся целым.
 * - Если файловая система блокировки не поддерживает, работаем как раньше, без них.
 */
object PinFileIo {
    fun readLocked(file: File): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val lock = tryLock(raf.channel, shared = true)
            if (lock === BUSY) throw FileBusyException()
            try {
                return readAll(raf)
            } finally {
                (lock as? java.nio.channels.FileLock)?.release()
            }
        }
    }

    /** Под исключительной блокировкой: [transform] получает текущие байты, возвращает результат и новые байты. */
    fun <T> update(file: File, transform: (ByteArray) -> Pair<T, ByteArray>): T {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            val lock = tryLock(raf.channel, shared = false)
            if (lock === BUSY) throw FileBusyException()
            try {
                val (result, bytes) = transform(readAll(raf))
                raf.seek(0)
                raf.write(bytes)
                raf.setLength(bytes.size.toLong())
                raf.channel.force(true)
                // Проверка: в файле ровно то, что записали (иначе правки остаются в памяти и запись повторится).
                if (!readAll(raf).contentEquals(bytes)) throw java.io.IOException("после записи в файле не то, что записано")
                return result
            } finally {
                (lock as? java.nio.channels.FileLock)?.release()
            }
        }
    }

    private val BUSY = Any()

    /** Блокировка; [BUSY] — держит другой процесс или компьютер; null — блокировки не поддерживаются. */
    private fun tryLock(channel: java.nio.channels.FileChannel, shared: Boolean): Any? {
        // Несколько коротких попыток: чужая запись обычно длится доли секунды.
        repeat(5) { attempt ->
            val lock = try {
                channel.tryLock(0L, Long.MAX_VALUE, shared)
            } catch (e: java.nio.channels.OverlappingFileLockException) {
                null
            } catch (e: java.io.IOException) {
                // Windows: «процесс не может получить доступ к файлу, так как часть файла заблокирована».
                if (e.message?.contains("lock", ignoreCase = true) == true || e.message?.contains("блок", ignoreCase = true) == true) {
                    null
                } else {
                    return null
                }
            } catch (e: UnsupportedOperationException) {
                return null
            }
            if (lock != null) return lock
            if (attempt < 4) Thread.sleep(150)
        }
        return BUSY
    }

    private fun readAll(raf: RandomAccessFile): ByteArray {
        raf.seek(0)
        val size = raf.length()
        require(size <= Int.MAX_VALUE) { "файл слишком большой" }
        val bytes = ByteArray(size.toInt())
        raf.readFully(bytes)
        return bytes
    }
}
