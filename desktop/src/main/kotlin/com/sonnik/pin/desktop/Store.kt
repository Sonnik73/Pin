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
class PinStore(
    private val scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Копия на компьютере и запоминание файла — подменяются в тестах. */
    private val local: LocalCopy = LocalCopy(),
    initialPath: String? = DesktopPrefs.path,
    private val rememberPath: (String) -> Unit = { DesktopPrefs.path = it },
) {
    var path by mutableStateOf(initialPath)
        private set
    var loaded by mutableStateOf<Loaded?>(null)
        private set
    /** Сообщение о затянувшейся беде с файлом; null — всё хорошо. */
    var problem by mutableStateOf<String?>(null)
        private set
    /** Есть правки, ещё не записанные в файл. */
    var unsaved by mutableStateOf(false)
        private set
    /** Общий файл сейчас недоступен (выключен компьютер с папкой, нет сети) — работаем с копией на этом компьютере. */
    var offline by mutableStateOf(false)
        private set
    /** С какого момента нет связи с файлом. */
    var offlineSince by mutableStateOf<Long?>(null)
        private set

    private val lock = Mutex()
    private var knownModified = -1L
    /** Байты файла, прочитанные или записанные последними, и данные в них: если файл с тех пор не менялся, не разбираем его снова. */
    private var lastFile: Pair<ByteArray, PinData>? = null
    /** Правки, которые не удалось записать: данные целиком и снятые отметки журнала. */
    private var pending: PinData? = null
    private var readFailures = 0
    private var writeFailures = 0

    init {
        // Сразу — то, что было на экране в прошлый раз: задачи видны, даже если общий файл сейчас недоступен.
        // Несохранённые тогда правки снова ждут записи.
        path?.let { p ->
            local.load(p, zone)?.let { copy ->
                loaded = Loaded(copy.data, -1L)
                if (copy.unsaved) {
                    pending = copy.data
                    unsaved = true
                }
            }
        }
    }

    private fun goOffline() {
        if (!offline) {
            offline = true
            offlineSince = System.currentTimeMillis()
        }
    }

    private fun goOnline() {
        offline = false
        offlineSince = null
    }

    /** Запомнить на этом компьютере то, что на экране, и ждут ли правки записи. */
    /** Копия на этом компьютере; [encoded] — уже готовый Pin.zip с этими данными (чтобы не упаковывать второй раз). */
    private suspend fun keepLocal(encoded: ByteArray? = null) {
        val p = path ?: return
        val data = loaded?.data ?: return
        val unsavedNow = pending != null
        withContext(Dispatchers.IO) { runCatching { local.save(p, data, unsavedNow, zone, encoded) } }
    }

    fun choose(newPath: String) {
        rememberPath(newPath)
        path = newPath
        knownModified = -1L
        lastFile = null
        pending = null
        unsaved = false
        problem = null
        readFailures = 0
        writeFailures = 0
        goOnline()
        // У каждого файла своя копия: несохранённые правки прежнего файла остаются в его копии и уедут в него,
        // когда его снова откроют; у этого файла — подхватываем его собственные.
        val copy = local.load(newPath, zone)
        loaded = copy?.let { Loaded(it.data, -1L) }
        if (copy?.unsaved == true) {
            pending = copy.data
            unsaved = true
        }
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
                        val today = DesktopPrefs.logicalDay().today()
                        file.writeBytes(Edits.toBytes(Edits.seedHolidays(PinData.EMPTY, today, now), zone))
                    }
                }.exceptionOrNull()
            }
            if (error != null) problem = "Не удалось создать файл: ${error.reason()}" else choose(newPath)
        }
    }

    fun reload() {
        knownModified = -1L
        lastFile = null
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

    /** Один шаг слежения: прочитать изменившийся файл или дописать отложенные правки. */
    internal suspend fun tick() = lock.withLock {
        if (pending != null) save() else read()
    }

    /** Перечитать, если файл изменился или прошлое чтение не удалось. */
    private suspend fun read() {
        val file = path?.let(::File) ?: return
        val modified = withContext(Dispatchers.IO) { if (file.isFile) file.lastModified() else 0L }
        if (modified == knownModified && readFailures == 0) return
        if (modified == 0L) {
            readFailures++
            // Есть копия на этом компьютере — тихо работаем с ней; без копии показать нечего — говорим прямо.
            if (loaded?.data != null) {
                goOffline()
            } else if (readFailures >= 3) {
                problem = "Файл не найден: ${file.path}. Если он на другом компьютере — проверьте, что тот включён и папка доступна; " +
                    "если на Google Диске — что Диск запущен."
            }
            return
        }
        val result = withContext(Dispatchers.IO) { runCatching { PinFileIo.readLocked(file).let { it to PinData.read(it, zone) } } }
        result.onSuccess { (bytes, data) ->
            knownModified = modified
            readFailures = 0
            problem = null
            goOnline()
            lastFile = bytes to data
            loaded = Loaded(data, modified)
            keepLocal(bytes)
        }.onFailure {
            readFailures++
            if (it !is FileBusyException && loaded?.data != null && !withContext(Dispatchers.IO) { file.isFile }) goOffline()
            // Диск часто отдаёт файл недокачанным: молчим полминуты и пробуем снова.
            if (readFailures >= 10) problem = "Не удаётся прочитать файл (${it.reason()}). Повторяю каждые 3 секунды."
            // Файл есть, но испорчен уже минуту (не занят и не недокачан): чиним его данными этого компьютера.
            val data = loaded?.data
            if (readFailures >= 20 && data != null && it is java.io.IOException && it !is FileBusyException) {
                pending = data
                unsaved = true
                readFailures = 0
                problem = "Файл Pin.zip был испорчен — записываю в него данные с этого компьютера."
            }
        }
    }

    /** Записать отложенные правки, слив их с тем, что сейчас в файле. */
    private suspend fun save() {
        val edited = pending ?: return
        val file = path?.let(::File) ?: return
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val known = lastFile
                val (merged, bytes) = PinFileIo.update(file) { current ->
                    // Испорченный файл (оборванная запись) не мешает: в памяти полные данные — они его и заменят.
                    // Файл тот же, что мы читали или писали последним, — его данные уже в памяти, разбирать незачем.
                    val onDisk = when {
                        current.isEmpty() -> PinData.EMPTY
                        known != null && known.first.contentEquals(current) -> known.second
                        else -> readOrEmpty(current)
                    }
                    val merged = Edits.merge(edited, onDisk)
                    val bytes = Edits.toBytes(merged, zone)
                    (merged to bytes) to bytes
                }
                Triple(merged, bytes, file.lastModified())
            }
        }
        result.onSuccess { (merged, bytes, modified) ->
            lastFile = bytes to merged
            pending = null
            unsaved = false
            writeFailures = 0
            knownModified = modified
            problem = null
            goOnline()
            loaded = Loaded(merged, modified)
            keepLocal(bytes)
        }.onFailure {
            writeFailures++
            // Папка недоступна — это не беда: правки лежат на этом компьютере и уедут, когда она вернётся.
            val reachable = withContext(Dispatchers.IO) { file.parentFile?.isDirectory == true }
            if (!reachable) {
                goOffline()
                writeFailures = 0
            } else if (writeFailures >= 10) {
                problem = "Не удаётся сохранить в файл (${it.reason()}). Правки не потеряны — сохраню, как только файл освободится."
            }
        }
    }

    private fun readOrEmpty(bytes: ByteArray): PinData =
        try {
            PinData.read(bytes, zone)
        } catch (e: java.io.IOException) {
            PinData.EMPTY
        }

    /** Применить правку: сразу на экране, затем — в файле (при неудаче — повтор каждые 3 секунды). */
    fun commit(change: (PinData) -> PinData) {
        scope.launch {
            lock.withLock {
                val current = loaded?.data ?: return@withLock
                // Правка, которая не удалась (например, лимит вложений), — сообщением, а не падением программы.
                val edited = try {
                    change(current)
                } catch (e: IllegalArgumentException) {
                    problem = e.message
                    return@withLock
                }
                if (edited === current) return@withLock
                pending = edited
                unsaved = true
                loaded = loaded?.copy(data = edited)
                // Сначала в файл; копия на этом компьютере пишется после (с теми же байтами) или, если файл недоступен, — здесь.
                save()
                if (pending != null) keepLocal()
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
        // Папку не создаём: её отсутствие значит «нет связи» (выключен компьютер с общей папкой, не запущен Диск).
        // Иначе рядом вырос бы новый пустой файл, а настоящий потом не нашёл бы дорогу обратно.
        if (file.parentFile?.isDirectory == false) throw java.io.FileNotFoundException("нет папки ${file.parent}")
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

/**
 * Копия на этом компьютере (%LOCALAPPDATA%\Pin) — отдельно для каждого общего файла: последнее, что было
 * на экране, и ждут ли правки записи. Нужна, чтобы при выключенном компьютере с общей папкой (или без сети)
 * задачи оставались под рукой и правились, а при закрытии программы или смене файла несохранённые правки
 * не терялись. Вложения лежат рядом отдельными файлами и пишутся только новые — копия не переписывает
 * их при каждой правке.
 */
class LocalCopy(private val dir: File = defaultDir()) {
    data class Copy(val data: PinData, val unsaved: Boolean)

    private fun key(path: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    private fun zipOf(path: String) = File(dir, "copy-${key(path)}.zip")
    private fun metaOf(path: String) = File(dir, "copy-${key(path)}.properties")
    private fun blobsOf(path: String) = File(dir, "copy-${key(path)}-attachments")

    fun save(path: String, data: PinData, unsaved: Boolean, zone: ZoneId, encoded: ByteArray? = null) {
        dir.mkdirs()
        // Сначала во временные файлы, потом подменяем: при сбое посреди записи старая копия остаётся целой.
        val tmpZip = File(dir, zipOf(path).name + ".tmp")
        // Готовый Pin.zip (может быть и с вложениями — при чтении они просто окажутся в памяти) или упаковать заново.
        tmpZip.writeBytes(encoded ?: Edits.toBytes(PinData(data.entries, data.people, data.lists, data.journal, emptyMap()), zone))
        val props = java.util.Properties().apply {
            setProperty("path", path)
            setProperty("unsaved", unsaved.toString())
        }
        val tmpMeta = File(dir, metaOf(path).name + ".tmp")
        tmpMeta.outputStream().use { props.store(it, "Pin: копия на этом компьютере") }
        // Вложения: новые — дописать, ненужные — убрать.
        val blobs = blobsOf(path)
        val wanted = data.entries.filter { !it.deleted }.flatMap { com.sonnik.pin.engine.Attachments.parse(it.attachments) }
        for (a in wanted) {
            val bytes = data.blob(a) ?: continue
            val f = File(blobs, "${a.id}/${a.name}")
            if (f.isFile && f.length() == bytes.size.toLong()) continue
            f.parentFile.mkdirs()
            f.writeBytes(bytes)
        }
        val ids = wanted.mapTo(HashSet()) { it.id }
        blobs.listFiles()?.filter { it.isDirectory && it.name !in ids }?.forEach { it.deleteRecursively() }
        java.nio.file.Files.move(tmpZip.toPath(), zipOf(path).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        java.nio.file.Files.move(tmpMeta.toPath(), metaOf(path).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    /** Копия для файла [path] (своя у каждого файла). */
    fun load(path: String, zone: ZoneId): Copy? = runCatching {
        val zip = zipOf(path).takeIf { it.isFile } ?: legacyZip(path) ?: return null
        val meta = metaOf(path).takeIf { it.isFile } ?: File(dir, "local.properties")
        val props = java.util.Properties().apply { if (meta.isFile) meta.inputStream().use { load(it) } }
        if (props.getProperty("path") != path) return null
        val data = PinData.read(zip.readBytes(), zone)
        val blobs = blobsOf(path)
        val restored = data.entries.flatMap { com.sonnik.pin.engine.Attachments.parse(it.attachments) }.mapNotNull { a ->
            File(blobs, "${a.id}/${a.name}").takeIf { it.isFile }?.let { a.path to it.readBytes() }
        }.toMap()
        Copy(PinData(data.entries, data.people, data.lists, data.journal, data.blobs + restored), props.getProperty("unsaved") == "true")
    }.getOrNull()

    /** Копия из 0.67 (один файл на все пути). */
    private fun legacyZip(path: String): File? {
        val meta = File(dir, "local.properties").takeIf { it.isFile } ?: return null
        val props = java.util.Properties().apply { meta.inputStream().use { load(it) } }
        return File(dir, "local.zip").takeIf { props.getProperty("path") == path && it.isFile }
    }

    companion object {
        fun defaultDir(): File =
            System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { File(it, "Pin") }
                ?: File(System.getProperty("user.home"), ".pin")

        /** При старте убрать временные файлы, оставшиеся от прерванной записи (save() подменяет их атомарно). */
        fun cleanOrphanedTemp() {
            runCatching {
                defaultDir().listFiles()?.filter { it.isFile && it.name.endsWith(".tmp") }?.forEach { it.delete() }
            }
        }
    }
}
