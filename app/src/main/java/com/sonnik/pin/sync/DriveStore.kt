package com.sonnik.pin.sync

import java.security.MessageDigest

/** Файл в папке «Pin» на Диске. [folder] — подпапка (`tasks`, `people`…), [key] — ключ записи из appProperties. */
data class RemoteFile(
    val id: String,
    val name: String,
    val folder: String,
    val key: String?,
    val md5: String?,
    val modified: Long,
    val conflict: Boolean = false,
)

/** Хранилище файлов: Google Диск или (в тестах) память. */
interface DriveStore {
    suspend fun listAll(): List<RemoteFile>
    suspend fun download(file: RemoteFile): String
    suspend fun create(folder: String, name: String, key: String?, text: String, conflict: Boolean = false): RemoteFile
    suspend fun update(file: RemoteFile, folder: String, name: String, text: String): RemoteFile
}

fun md5Hex(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Нужен повторный вход в Google (доступ отозван или истёк). */
class NeedsSignInException(message: String = "Нужно войти в Google заново") : Exception(message)
