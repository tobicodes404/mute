package com.tobicodes.mute.core.util

object TextCleanup {

    /** Cleans raw PDF text for natural TTS reading */
    fun clean(raw: String): String {
        var t = raw.replace("\r", "")
        t = t.replace(Regex("-\\n(?=\\p{L})"), "")      // de-hyphenate
        t = t.replace(Regex("\\n(?=\\p{Ll})"), " ")     // join wrapped lines
        t = t.replace(Regex("\\n{2,}"), "\n")
        t = t.replace(Regex("[ \\t]+"), " ")
        return t.trim()
    }

    /** Removes standalone page numbers (123, -45-, xii, IV ...) */
    fun removePageNumbers(text: String): String =
        text.lines().filter { line ->
            val t = line.trim()
            !(t.matches(Regex("^\\d{1,4}$")) ||
              t.matches(Regex("^[-–—]?\\s*\\d{1,4}\\s*[-–—]?$")) ||
              t.matches(Regex("^[ivxlcdmIVXLCDM]{1,6}$")))
        }.joinToString("\n")

    /** Removes running headers/footers: lines repeated on 50%+ pages */
    fun removeRepeatedLines(pages: List<String>): List<String> {
        if (pages.size < 4) return pages
        val counts = mutableMapOf<String, Int>()
        for (page in pages) {
            page.lines().map { it.trim() }
                .filter { it.length in 3..80 }
                .toSet()
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val threshold = pages.size * 0.5
        val bad = counts.filterValues { it >= threshold }.keys
        if (bad.isEmpty()) return pages
        return pages.map { page ->
            page.lines().filter { it.trim() !in bad }.joinToString("\n")
        }
    }
}
