package com.sonnik.pin.engine

/** Вложение к задаче: файл хранится отдельно, в записи — только id и имя. */
data class Attachment(val id: String, val name: String) {
    /** Путь в архиве и папке синхронизации: `attachments/<id>/<имя>`. */
    val path: String get() = "attachments/$id/$name"
    val mime: String get() = Attachments.mimeOf(name)
    val isImage: Boolean get() = mime.startsWith("image/")
}

object Attachments {
    /** Не больше 10 МБ на файл — чтобы синхронизация через Pin.zip не тяжелела. */
    const val MAX_BYTES = 10 * 1024 * 1024
    /** Все вложения вместе: Pin.zip переписывается целиком при каждой синхронизации, большой архив — долго и тяжело. */
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    /** Сколько мегабайт — для сообщений. */
    fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0).replace('.', ',')

    /** Строка для базы и для поля `attachments` в .md: «id/имя» через перевод строки. */
    fun format(list: List<Attachment>): String? = list.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.id}/${it.name}" }

    fun parse(text: String?): List<Attachment> = text.orEmpty().lines().mapNotNull(::parseOne)

    fun parseOne(raw: String): Attachment? {
        val s = raw.trim().trim('"')
        val slash = s.indexOf('/')
        if (slash <= 0 || slash == s.length - 1) return null
        val id = s.substring(0, slash)
        if (!id.all { it.isLetterOrDigit() || it == '-' }) return null
        return Attachment(id, safeName(s.substring(slash + 1)))
    }

    /** Путь в архиве → вложение («attachments/<id>/<имя>»); всё прочее — null. */
    fun fromPath(path: String): Attachment? {
        val p = path.replace('\\', '/').removePrefix("Pin/")
        if (!p.startsWith("attachments/")) return null
        return parseOne(p.removePrefix("attachments/"))
    }

    /** Имя файла без путей и опасных символов, не длиннее 80 знаков (расширение сохраняется). */
    fun safeName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[\u0000-\u001f<>:"|?*]"""), "_")
            .trim().trimStart('.')
            .ifEmpty { "файл" }
        if (base.length <= 80) return base
        val ext = base.substringAfterLast('.', "").take(10)
        return base.take(80 - ext.length - 1).trimEnd() + if (ext.isEmpty()) "" else ".$ext"
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    /** Значок по типу файла. */
    fun icon(name: String): String = when {
        mimeOf(name).startsWith("image/") -> "🖼"
        mimeOf(name) == "application/pdf" -> "📄"
        mimeOf(name).startsWith("audio/") -> "🎵"
        mimeOf(name).startsWith("video/") -> "🎬"
        else -> "📎"
    }
}
