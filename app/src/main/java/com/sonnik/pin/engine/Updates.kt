package com.sonnik.pin.engine

/** Выпуск на GitHub: версия, ссылки и что нового. */
data class ReleaseInfo(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String,
    /** День публикации (эпохальный, по часовому поясу телефона). */
    val publishedDay: Long? = null,
    /** Размер APK, байт. */
    val apkBytes: Long? = null,
)

/** Проверка обновлений — чистая часть: сравнение версий и когда пора проверять снова. */
object Updates {
    /** Адрес последнего выпуска (публичный репозиторий, без ключей и входа). */
    const val LATEST_URL = "https://api.github.com/repos/Sonnik73/Pin/releases/latest"

    /** Не чаще раза в 6 часов — при каждом входе в приложение не дёргаем GitHub. */
    const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** «v0.35» → [0, 35]; «1.2.3-beta» → [1, 2, 3]. Непонятное — пустой список. */
    fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
            .map { it.takeWhile(Char::isDigit) }
            .takeIf { list -> list.isNotEmpty() && list.all { it.isNotEmpty() } }
            ?.map { it.toInt() }
            .orEmpty()

    /** Новее ли [candidate], чем [current]. «0.10» новее «0.9»: сравниваем числа, а не строки. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** «4,2 МБ», «850 КБ». */
    fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f МБ".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024.0).replace('.', ',')
        else -> "${(bytes + 1023) / 1024} КБ"
    }

    fun isDue(lastCheck: Long, now: Long): Boolean = now - lastCheck >= INTERVAL_MS || now < lastCheck

    /** Показать ли предложение: новее текущей и не та, что пользователь попросил пропустить. */
    fun shouldOffer(release: ReleaseInfo?, current: String, skipped: String?): Boolean =
        release != null && isNewer(release.version, current) && parts(release.version) != skipped?.let(::parts)

    /** Текст выпуска без первого заголовка «## 0.35» — он и так в заголовке окна. */
    fun cleanNotes(body: String): String =
        body.lines().dropWhile { it.isBlank() || it.trimStart().startsWith("#") }.joinToString("\n").trim()
}
