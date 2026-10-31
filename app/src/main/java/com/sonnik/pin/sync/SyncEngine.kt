package com.sonnik.pin.sync

import com.sonnik.pin.data.PinRepository
import com.sonnik.pin.engine.MdDoc
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Итог синхронизации. */
data class SyncReport(val downloaded: Int, val uploaded: Int, val created: Int, val conflicts: Int) {
    fun describe(): String = buildString {
        append("Получено файлов: $downloaded, отправлено: ${uploaded + created}")
        if (conflicts > 0) append(", конфликтов: $conflicts (копии — в папке Pin/conflicts)")
    }
}

/**
 * Синхронизация папки .md-файлов с Диском.
 *
 * 1. Скачиваются файлы, чьё содержимое отличается от локального (по MD5), и сливаются с базой: запись побеждает,
 *    если её `updated` новее; журнал объединяется без дублей.
 * 2. Если запись с последней синхронизации менялась и тут, и на Диске, проигравшая версия сохраняется копией
 *    в `Pin/conflicts` (такие файлы не читаются обратно).
 * 3. Локальные файлы, которых нет на Диске или которые отличаются, отправляются туда. Удаления — пометкой `deleted`.
 */
class SyncEngine(private val repo: PinRepository, private val store: DriveStore, private val zone: ZoneId) {
    suspend fun sync(): SyncReport {
        val startedAt = repo.now()
        val lastSync = repo.settings().lastSyncAt
        val remoteAll = store.listAll()
        val remote = remoteAll.filter { !it.conflict && it.key != null }.groupBy { it.key!! }.mapValues { it.value.first() }

        // 1. Скачать отличающееся.
        val localBefore = repo.exportFiles(includeDeleted = true).associateBy { it.key }
        val changedRemote = remote.values.filter { r -> localBefore[r.key]?.let { md5Hex(it.text) != r.md5 } ?: true }
        val downloaded = changedRemote.map { r -> r to store.download(r) }
        if (downloaded.isNotEmpty()) {
            repo.importFiles(downloaded.map { (r, text) -> "${r.folder}/${r.name}" to text })
        }

        // 2. Конфликты: обе стороны менялись после прошлой синхронизации.
        var conflicts = 0
        if (lastSync > 0) {
            val localAfter = repo.exportFiles(includeDeleted = true).associateBy { it.key }
            for ((r, remoteText) in downloaded) {
                val key = r.key ?: continue
                if (key.startsWith("j:")) continue
                val before = localBefore[key] ?: continue
                val localChanged = (repo.updatedAtOf(key) ?: 0L) > lastSync || updatedOf(before.text) > lastSync
                if (r.modified <= lastSync || !localChanged || before.text == remoteText) continue
                val after = localAfter[key]?.text
                val loser = if (after == remoteText || updatedOf(remoteText) > updatedOf(before.text)) before.text else remoteText
                val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").withZone(zone).format(Instant.ofEpochMilli(startedAt))
                store.create("conflicts", "${r.name.removeSuffix(".md")} (конфликт $stamp).md", null, loser, conflict = true)
                conflicts++
            }
        }

        // 3. Отправить новое и изменённое.
        var uploaded = 0
        var created = 0
        val fresh = remote.toMutableMap()
        for (file in repo.exportFiles(includeDeleted = true)) {
            val r = fresh[file.key]
            if (r == null) {
                fresh[file.key] = store.create(file.folder, file.name, file.key, file.text)
                created++
            } else if (r.md5 != md5Hex(file.text) || r.name != file.name || r.folder != file.folder) {
                fresh[file.key] = store.update(r, file.folder, file.name, file.text)
                uploaded++
            }
        }
        repo.updateSettingsQuietly { it.copy(lastSyncAt = startedAt, lastSyncError = null) }
        return SyncReport(downloaded.size, uploaded, created, conflicts)
    }

    private fun updatedOf(text: String): Long = runCatching { MdDoc.parse(text).instant("updated", zone) }.getOrNull() ?: 0L

}
