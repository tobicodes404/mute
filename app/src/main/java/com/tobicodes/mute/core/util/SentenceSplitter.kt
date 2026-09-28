package com.tobicodes.mute.core.util

object SentenceSplitter {
    /** Splits text into small TTS-friendly sentences (supports Bangla । too) */
    fun split(text: String): List<String> {
        val normalized = text.replace("\n", " ")
        val parts = normalized.split(Regex("(?<=[।.!?])\\s*"))
        val result = mutableListOf<String>()
        for (part in parts) {
            val p = part.trim()
            if (p.isEmpty()) continue
            if (result.isNotEmpty() && (p.length < 3 || result.last().length < 12)) {
                result[result.size - 1] = result.last() + " " + p
            } else {
                result.add(p)
            }
        }
        return result
    }
}
