package com.sonnik.pin.engine

/** Текст, присланный через «Поделиться»: что поставить в строку быстрого добавления и что — в описание. */
data class SharedTask(val line: String, val body: String)

object ShareText {
    private val URL = Regex("""https?://[^\s<>"]+""")

    /**
     * Заголовок — тема письма/страницы, иначе первая строка текста без ссылок (до 120 знаков);
     * ссылки и остальной текст — в описание. Одна ссылка без текста — «Посмотреть: сайт».
     */
    fun parse(subject: String?, text: String?): SharedTask {
        val raw = text?.trim().orEmpty()
        val urls = URL.findAll(raw).map { it.value.trimEnd('.', ',', ')', '»') }.distinct().toList()
        val withoutUrls = URL.replace(raw, "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = subject?.trim()?.takeIf { it.isNotEmpty() }
            ?: withoutUrls.firstOrNull()
            ?: urls.firstOrNull()?.let { "Посмотреть: " + host(it) }
            ?: ""
        val restLines = if (subject.isNullOrBlank()) withoutUrls.drop(1) else withoutUrls
        val body = (restLines + urls).joinToString("\n")
        return SharedTask(title.take(120).trim(), body)
    }

    fun host(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")
}
