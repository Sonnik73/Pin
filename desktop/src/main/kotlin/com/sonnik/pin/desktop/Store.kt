package com.sonnik.pin.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Прочитанный файл: содержимое, время изменения файла и ошибка чтения или записи. */
data class Loaded(val data: PinData?, val modified: Long, val error: String?)

/**
 * Pin.zip на компьютере: чтение при каждом изменении файла (телефон синхронизировался) и запись правок.
 * Перед записью файл перечитывается и сливается с правками (новее — побеждает), чтобы не затереть
 * то, что телефон успел записать. Файл перезаписывается на месте, как это делает телефон:
 * Google Диск сохраняет у него тот же идентификатор, и ссылка на файл в телефоне не ломается.
 */
class PinStore(private val scope: CoroutineScope, private val zone: ZoneId = ZoneId.systemDefault()) {
    var path by mutableStateOf(DesktopPrefs.path)
        private set
    var loaded by mutableStateOf<Loaded?>(null)
        private set
    /** Идёт запись в файл. */
    var saving by mutableStateOf(false)
        private set

    private val lock = Mutex()
    private var knownModified = -1L

    fun choose(newPath: String) {
        DesktopPrefs.path = newPath
        path = newPath
        knownModified = -1L
        loaded = null
        scope.launch { checkFile() }
    }

    fun reload() {
        knownModified = -1L
        scope.launch { checkFile() }
    }

    /** Следить за файлом: вызывать из долгой корутины. */
    suspend fun watch() {
        while (true) {
            checkFile()
            delay(3_000)
        }
    }

    private suspend fun checkFile() = lock.withLock {
        val file = path?.let(::File) ?: return@withLock
        val modified = withContext(Dispatchers.IO) { if (file.isFile) file.lastModified() else 0L }
        if (modified == knownModified) return@withLock
        knownModified = modified
        loaded = withContext(Dispatchers.IO) {
            if (!file.isFile) {
                Loaded(loaded?.data, 0, "Файл не найден: ${file.path}")
            } else {
                runCatching { Loaded(PinData.read(file.readBytes(), zone), modified, null) }
                    .getOrElse { Loaded(loaded?.data, modified, "Не удалось прочитать файл: ${it.message ?: it.javaClass.simpleName}") }
            }
        }
    }

    /** Применить правку: сразу на экране, затем — в файле. */
    fun commit(change: (PinData) -> PinData) {
        scope.launch {
            lock.withLock {
                val current = loaded?.data ?: return@withLock
                val file = path?.let(::File) ?: return@withLock
                val edited = change(current)
                if (edited === current) return@withLock
                loaded = loaded?.copy(data = edited)
                saving = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val onDisk = if (file.isFile) PinData.read(file.readBytes(), zone) else PinData.EMPTY
                        val removed = Edits.journalKeys(current) - Edits.journalKeys(edited)
                        val merged = Edits.merge(edited, onDisk, removed)
                        val bytes = Edits.toBytes(merged, zone)
                        file.outputStream().use { it.write(bytes) }
                        merged to file.lastModified()
                    }
                }
                saving = false
                result.onSuccess { (merged, modified) ->
                    knownModified = modified
                    loaded = Loaded(merged, modified, null)
                }.onFailure {
                    loaded = loaded?.copy(error = "Не удалось записать файл: ${it.message ?: it.javaClass.simpleName}")
                }
            }
        }
    }
}
