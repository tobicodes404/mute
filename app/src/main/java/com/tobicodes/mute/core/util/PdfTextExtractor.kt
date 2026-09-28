package com.tobicodes.mute.core.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTextExtractor {

    /** Extracts cleaned, reading-order-correct text per page */
    suspend fun extractPages(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("mute_extract_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext emptyList()

            val document = PDDocument.load(tempFile)

            val raw = try {
                // Column-aware extraction
                val stripper = StructuredStripper()
                stripper.startPage = 1
                stripper.endPage = document.numberOfPages
                stripper.getText(document)
                stripper.extractedPages
            } catch (e: Exception) {
                // Fallback: plain extraction
                val fallback = PDFTextStripper()
                (1..document.numberOfPages).map { p ->
                    fallback.startPage = p
                    fallback.endPage = p
                    fallback.getText(document)
                }
            }

            document.close()

            // Cleanup pipeline: clean → page numbers → repeated headers/footers
            val cleaned = raw.map { TextCleanup.removePageNumbers(TextCleanup.clean(it)) }
            TextCleanup.removeRepeatedLines(cleaned)
        } catch (e: Exception) {
            emptyList()
        } finally {
            tempFile.delete()
        }
    }
}
