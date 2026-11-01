package com.sonnik.pin.engine

/** Раздел журнала изменений: «## 0.13» и пункты «- …». */
data class ChangelogEntry(val version: String, val items: List<String>)

object Changelog {
    fun parse(text: String): List<ChangelogEntry> {
        val entries = ArrayList<ChangelogEntry>()
        var version: String? = null
        val items = ArrayList<String>()
        fun flush() {
            version?.let { entries += ChangelogEntry(it, items.toList()) }
            items.clear()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    version = line.removePrefix("## ").trim()
                }
                line.startsWith("- ") && version != null -> items += line.removePrefix("- ").trim()
                // Продолжение пункта на следующей строке.
                line.isNotEmpty() && !line.startsWith("#") && version != null && items.isNotEmpty() ->
                    items[items.lastIndex] = items.last() + " " + line
            }
        }
        flush()
        return entries
    }
}
