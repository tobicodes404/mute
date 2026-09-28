package com.tobicodes.mute.core.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class OcrEngine(context: Context) {

    private val appContext = context.applicationContext
    private val api = TessBaseAPI()
    val isReady: Boolean

    init {
        ensureData()
        val langs = availableLanguages()
        isReady = langs.isNotEmpty() &&
            runCatching {
                val ok = api.init(appContext.filesDir.absolutePath, langs)
                if (ok) {
                    // Better spacing accuracy for book text
                    api.setVariable("preserve_interword_spaces", "1")
                }
                ok
            }.getOrDefault(false)
    }

    private fun ensureData() {
        val dir = File(appContext.filesDir, "tessdata")
        if (!dir.exists()) dir.mkdirs()
        for (name in listOf("eng.traineddata", "ben.traineddata")) {
            val target = File(dir, name)
            if (!target.exists() || target.length() < 1_000_000) {
                runCatching {
                    appContext.assets.open("tessdata/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun availableLanguages(): String {
        val dir = File(appContext.filesDir, "tessdata")
        val hasEng = File(dir, "eng.traineddata").let { it.exists() && it.length() > 1_000_000 }
        val hasBen = File(dir, "ben.traineddata").let { it.exists() && it.length() > 1_000_000 }
        return when {
            hasBen && hasEng -> "ben+eng"
            hasBen -> "ben"
            hasEng -> "eng"
            else -> ""
        }
    }

    fun recognize(bitmap: Bitmap): String = try {
        api.setImage(bitmap)
        api.utF8Text?.trim() ?: ""
    } catch (e: Exception) {
        ""
    }

    fun shutdown() = runCatching { api.end() }
}
