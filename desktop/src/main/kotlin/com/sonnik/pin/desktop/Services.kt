package com.sonnik.pin.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sonnik.pin.engine.EntryType
import com.sonnik.pin.engine.GiftInfo
import com.sonnik.pin.engine.GiftStatus
import com.sonnik.pin.engine.HabitAlarm
import com.sonnik.pin.engine.MorningSummary
import com.sonnik.pin.engine.Occasion
import com.sonnik.pin.engine.Occasions
import com.sonnik.pin.engine.ReleaseInfo
import com.sonnik.pin.engine.Reminders
import com.sonnik.pin.engine.Rollover
import com.sonnik.pin.engine.TaskSnapshot
import com.sonnik.pin.engine.Updates
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.prefs.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val prefs: Preferences = Preferences.userRoot().node("com/sonnik/pin")

private fun flush() = runCatching { prefs.flush() }

/** Открытые задачи с досчитанным переносом — для напоминаний (как телефон после ночного переноса). */
internal fun PinData.openSnapshots(today: Long): List<TaskSnapshot> =
    entries.asSequence()
        .filter { !it.deleted && !it.disabled && it.dueDay != null && (it.entryType == EntryType.ONE_TIME || it.entryType == EntryType.RECURRING) }
        .mapNotNull { e -> e.toSnapshot()?.let { s -> Rollover.apply(s, today, e.schedule)?.task ?: s } }
        .toList()

/** День рождения или праздник на ближайшую дату (если телефон ещё не передвинул прошедшую). */
private fun PinData.nextDay(e: com.sonnik.pin.data.EntryEntity, today: Long): Long? {
    val due = e.dueDay ?: return null
    if (due >= today) return due
    val s = e.schedule ?: return null
    return s.rule.nextAfter(today - 1, s.anchor)
}

/** Напоминания о днях рождения и праздниках на сегодня — те же правила, что на телефоне. */
internal fun PinData.occasionTexts(today: Long): Pair<List<String>, List<String>> {
    val names = people.associate { it.id to it.name }
    val list = entries.filter {
        !it.deleted && !it.disabled && (it.entryType == EntryType.BIRTHDAY || it.entryType == EntryType.HOLIDAY) && it.dueDay != null
    }
    val days = list.associate { it.id to nextDay(it, today) }
    val holidaysToday = list.filter { it.entryType == EntryType.HOLIDAY && days[it.id] == today }.map { "🎉 " + it.title }
    val occasions = list.mapNotNull { e ->
        val day = days[e.id] ?: return@mapNotNull null
        Occasion(
            entryId = e.id,
            type = e.entryType,
            title = if (e.entryType == EntryType.BIRTHDAY) names[e.personId] ?: e.title else e.title,
            day = day,
            personId = e.personId,
            age = if (e.entryType == EntryType.BIRTHDAY) Occasions.ageOn(e.birthYear, day) else null,
        )
    }
    val byId = list.associateBy { it.id }
    val gifts = entries.filter { !it.deleted && it.entryType == EntryType.GIFT }.map { GiftInfo(it.id, it.occasionId, it.personId, GiftStatus.parse(it.giftStatus)) }
    val notices = Occasions.notices(occasions, { Occasions.parseRemindDays(byId[it.entryId]?.remindDays) }, gifts, today)
    return holidaysToday to notices.map { n -> Occasions.describe(n) + if (n.needGift) " — подарок ещё не куплен" else "" }
}

/**
 * Напоминания на компьютере: время задач, привычки, утренняя сводка с поводами. Показываются уведомлениями Windows,
 * пока Pin запущен (можно в трее). Каждое — ровно один раз: помним, до какого момента уже показали.
 */
object DesktopReminders {
    var enabled by mutableStateOf(prefs.getBoolean("remind_enabled", true))
        private set
    var morningMinutes by mutableStateOf(prefs.getInt("morning_minutes", 9 * 60))
        private set

    fun switch(on: Boolean) {
        enabled = on
        prefs.putBoolean("remind_enabled", on)
        flush()
    }

    fun setMorning(minutes: Int) {
        morningMinutes = minutes
        prefs.putInt("morning_minutes", minutes)
        flush()
    }

    /** Что пора показать сейчас: (заголовок, текст). Вызывать раз в полминуты. */
    fun check(data: PinData, today: Long, now: Long, zone: ZoneId, boundary: Int): List<Pair<String, String>> {
        if (!enabled) return emptyList()
        // После долгого перерыва (программа была закрыта) старые напоминания не вываливаем: только последние 15 минут.
        val last = prefs.getLong("remind_last", 0L).let { if (it == 0L || now - it > 15 * 60_000L || it > now) now - 15 * 60_000L else it }
        val result = ArrayList<Pair<String, String>>()
        for (r in Reminders.due(data.openSnapshots(today), last, now, zone, boundary)) {
            result += "Pin — пора" to r.title
        }
        if (com.sonnik.pin.engine.Feature.HABITS.on()) {
            val alarms = data.entries.filter { !it.deleted && !it.disabled && it.entryType == EntryType.HABIT && it.timeMinutes != null }
                .map { HabitAlarm(data.habitInfo(it, today), it.title, it.timeMinutes!!) }
            for (r in Reminders.habitsDue(alarms, today, last, now, zone, boundary)) result += "Pin — привычка" to r.title
        }
        val lastMorning = prefs.getLong("morning_last_day", Long.MIN_VALUE)
        if (Reminders.morningDue(today, lastMorning, now, morningMinutes, zone, boundary)) {
            prefs.putLong("morning_last_day", today)
            val (holidays, occasions) = data.occasionTexts(today)
            MorningSummary.of(data.board(today), holidays)?.let { result += it.title to it.text }
            occasions.forEach { result += "Pin — скоро" to it }
        }
        prefs.putLong("remind_last", now)
        flush()
        return result
    }
}

/** Сворачивание в трей при закрытии окна: напоминания продолжают приходить. */
object TrayMode {
    var onClose by mutableStateOf(prefs.getBoolean("tray_on_close", true))
        private set
    /** Подсказку «Pin работает в трее» показываем один раз. */
    var hinted: Boolean
        get() = prefs.getBoolean("tray_hinted", false)
        set(value) {
            prefs.putBoolean("tray_hinted", value)
            flush()
        }

    fun set(on: Boolean) {
        onClose = on
        prefs.putBoolean("tray_on_close", on)
        flush()
    }
}

/** Запуск вместе с Windows: строка в HKCU\…\Run на установленную программу (в трее, без окна). */
object Autostart {
    private const val KEY = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""

    /** Путь к Pin.exe — есть только у установленной программы. */
    val exe: String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    /** Включено ли — по самому реестру (его могли поменять руками или в «Автозагрузке» Windows). */
    var enabled by mutableStateOf(exe != null && registered())
        private set

    private fun registered(): Boolean = runCatching {
        val p = ProcessBuilder("reg", "query", KEY, "/v", "Pin").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && out.contains("Pin")
    }.getOrDefault(false)

    fun set(on: Boolean): Boolean {
        val path = exe ?: return false
        val cmd = if (on) {
            listOf("reg", "add", KEY, "/v", "Pin", "/t", "REG_SZ", "/d", "\"$path\" --tray", "/f")
        } else {
            listOf("reg", "delete", KEY, "/v", "Pin", "/f")
        }
        val ok = runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        enabled = registered()
        return ok
    }
}

/** Проверка новой версии на GitHub (как на телефоне: не чаще раза в 6 часов, выключается). */
object UpdateCheck {
    var enabled by mutableStateOf(prefs.getBoolean("updates_enabled", true))
        private set
    var available by mutableStateOf<ReleaseInfo?>(null)
        private set
    /** Ссылка на установщик .msi из выпуска. */
    var msiUrl by mutableStateOf<String?>(null)
        private set

    fun switch(on: Boolean) {
        enabled = on
        prefs.putBoolean("updates_enabled", on)
        flush()
        if (!on) available = null
    }

    fun skip() {
        available?.let { prefs.put("updates_skipped", it.version) }
        flush()
        available = null
    }

    /** Проверить, если пора (или [force]); вызывать не на главном потоке. Возвращает новую версию, если она появилась. */
    fun check(force: Boolean = false): ReleaseInfo? {
        if (!enabled && !force) return null
        val now = System.currentTimeMillis()
        if (!force && !Updates.isDue(prefs.getLong("updates_last", 0L), now)) return null
        val release = runCatching { fetch() }.getOrNull() ?: return null
        prefs.putLong("updates_last", now)
        flush()
        val skipped = if (force) null else prefs.get("updates_skipped", null)
        return if (Updates.shouldOffer(release, APP_VERSION, skipped)) release.also { available = it } else null
    }

    private fun fetch(): ReleaseInfo {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
        val request = HttpRequest.newBuilder(URI.create(Updates.LATEST_URL))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Pin-Windows/$APP_VERSION")
            .timeout(Duration.ofSeconds(15))
            .build()
        val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        val o = Json.parseToJsonElement(body).jsonObject
        val assets = o["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        val msi = assets.firstOrNull { it["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".msi", ignoreCase = true) }
        msiUrl = msi?.get("browser_download_url")?.jsonPrimitive?.content
        val published = o["published_at"]?.jsonPrimitive?.content?.let {
            runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }.getOrNull()
        }
        return ReleaseInfo(
            version = o["tag_name"]!!.jsonPrimitive.content,
            pageUrl = o["html_url"]?.jsonPrimitive?.content ?: "https://github.com/Sonnik73/Pin/releases/latest",
            apkUrl = null,
            notes = Updates.cleanNotes(o["body"]?.jsonPrimitive?.content.orEmpty()),
            publishedDay = published,
            apkBytes = msi?.get("size")?.jsonPrimitive?.longOrNull,
        )
    }
}
