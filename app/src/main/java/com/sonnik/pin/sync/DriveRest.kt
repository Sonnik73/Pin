package com.sonnik.pin.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Google Drive REST v3 без сторонних библиотек. Область `drive.file`: приложение видит только свои файлы,
 * поэтому папка «Pin» и всё в ней создаются самим Pin. Подпапки — по типам записей.
 */
class DriveRest(private val token: String) : DriveStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var rootId: String? = null
    private val folderIds = HashMap<String, String>()

    private suspend fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = if (method == "PATCH") "POST" else method
                if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 20_000
                readTimeout = 60_000
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
                }
            }
            try {
                body?.let { b -> conn.outputStream.use { it.write(b) } }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                when {
                    code == 401 -> throw NeedsSignInException()
                    code == 403 && text.contains("insufficient", ignoreCase = true) -> throw NeedsSignInException("Нет доступа к Диску — войдите заново")
                    code !in 200..299 -> throw IOException("Google Диск ответил $code: ${text.take(300)}")
                }
                text
            } finally {
                conn.disconnect()
            }
        }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun findFolder(name: String, parent: String): String? {
        val query = "mimeType='$FOLDER_MIME' and name='$name' and '$parent' in parents and trashed=false"
        val res = json.parseToJsonElement(request("GET", "$API/files?q=${q(query)}&fields=files(id)&spaces=drive")).jsonObject
        return res["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    private suspend fun createFolder(name: String, parent: String): String {
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            putJsonArray("parents") { add(JsonPrimitive(parent)) }
        }
        val res = request("POST", "$API/files?fields=id", meta.toString().toByteArray())
        return json.parseToJsonElement(res).jsonObject.getValue("id").jsonPrimitive.content
    }

    private suspend fun root(): String = rootId ?: (findFolder(ROOT, "root") ?: createFolder(ROOT, "root")).also { rootId = it }

    private suspend fun folder(name: String): String {
        folderIds[name]?.let { return it }
        val parent = root()
        return (findFolder(name, parent) ?: createFolder(name, parent)).also { folderIds[name] = it }
    }

    override suspend fun listAll(): List<RemoteFile> {
        val parent = root()
        // Подпапки корня.
        val foldersRes = json.parseToJsonElement(
            request("GET", "$API/files?q=${q("mimeType='$FOLDER_MIME' and '$parent' in parents and trashed=false")}&fields=files(id,name)&pageSize=100"),
        ).jsonObject
        foldersRes["files"]?.jsonArray?.forEach { f ->
            val o = f.jsonObject
            folderIds[o.getValue("name").jsonPrimitive.content] = o.getValue("id").jsonPrimitive.content
        }
        val out = ArrayList<RemoteFile>()
        for ((name, id) in folderIds.toMap()) {
            var pageToken: String? = null
            do {
                val url = "$API/files?q=${q("'$id' in parents and trashed=false and mimeType!='$FOLDER_MIME'")}" +
                    "&fields=${q("nextPageToken,files(id,name,md5Checksum,modifiedTime,appProperties)")}&pageSize=1000" +
                    (pageToken?.let { "&pageToken=${q(it)}" } ?: "")
                val res = json.parseToJsonElement(request("GET", url)).jsonObject
                res["files"]?.jsonArray?.forEach { f -> out += parse(f.jsonObject, name) }
                pageToken = res["nextPageToken"]?.jsonPrimitive?.contentOrNull
            } while (pageToken != null)
        }
        return out
    }

    private fun parse(o: JsonObject, folder: String): RemoteFile {
        val props = o["appProperties"]?.jsonObject
        return RemoteFile(
            id = o.getValue("id").jsonPrimitive.content,
            name = o.getValue("name").jsonPrimitive.content,
            folder = folder,
            key = props?.get(PROP_KEY)?.jsonPrimitive?.contentOrNull,
            md5 = o["md5Checksum"]?.jsonPrimitive?.contentOrNull,
            modified = o["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L,
            conflict = props?.get(PROP_CONFLICT)?.jsonPrimitive?.contentOrNull == "1" || folder == "conflicts",
        )
    }

    override suspend fun download(file: RemoteFile): String = request("GET", "$API/files/${file.id}?alt=media")

    private fun multipart(meta: JsonObject, text: String): Pair<ByteArray, String> {
        val boundary = "pin-" + UUID.randomUUID()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString()).append("\r\n")
            append("--$boundary\r\nContent-Type: text/markdown; charset=UTF-8\r\n\r\n")
            append(text).append("\r\n")
            append("--$boundary--\r\n")
        }
        return body.toByteArray(Charsets.UTF_8) to "multipart/related; boundary=$boundary"
    }

    override suspend fun create(folder: String, name: String, key: String?, text: String, conflict: Boolean): RemoteFile {
        val parent = folder(folder)
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", "text/markdown")
            putJsonArray("parents") { add(JsonPrimitive(parent)) }
            putJsonObject("appProperties") {
                key?.let { put(PROP_KEY, it) }
                if (conflict) put(PROP_CONFLICT, "1")
            }
        }
        val (body, type) = multipart(meta, text)
        val res = request("POST", "$UPLOAD/files?uploadType=multipart&fields=$FIELDS", body, type)
        return parse(json.parseToJsonElement(res).jsonObject, folder)
    }

    override suspend fun update(file: RemoteFile, folder: String, name: String, text: String): RemoteFile {
        val meta = buildJsonObject { put("name", name) }
        val move = if (folder != file.folder) "&addParents=${folder(folder)}&removeParents=${folder(file.folder)}" else ""
        val (body, type) = multipart(meta, text)
        val res = request("PATCH", "$UPLOAD/files/${file.id}?uploadType=multipart&fields=$FIELDS$move", body, type)
        return parse(json.parseToJsonElement(res).jsonObject, folder)
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val ROOT = "Pin"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val PROP_KEY = "pinKey"
        const val PROP_CONFLICT = "pinConflict"
        const val FIELDS = "id,name,md5Checksum,modifiedTime,appProperties"
    }
}
