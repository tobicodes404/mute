package com.tobicodes.mute.data.repository

import android.content.Context
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import com.tobicodes.mute.data.local.db.AppDatabase
import com.tobicodes.mute.domain.model.Document
import com.tobicodes.mute.domain.model.DocumentType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class DocumentRepository(private val database: AppDatabase) {
    
    private val documentDao = database.documentDao()

    fun getAllDocuments(): Flow<List<Document>> = documentDao.getAllDocuments()

    suspend fun getDocumentById(id: String): Document? = documentDao.getDocumentById(id)

    suspend fun addDocument(context: Context, uri: Uri): Document {
        val fileName = getFileName(context, uri)
        val fileSize = getFileSize(context, uri)
        val documentType = getDocumentType(uri)
        val id = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
        
        // ✅ CRITICAL: Take persistent URI permission (survives app restart)
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            // Some URIs don't support persistable permissions (e.g., file://)
            // That's okay — we'll handle access errors when opening
        }
        
        val document = Document(
            id = id,
            title = fileName,
            uriString = uri.toString(),
            type = documentType,
            sizeBytes = fileSize,
            createdAt = System.currentTimeMillis()
        )
        
        documentDao.insertDocument(document)
        return document
    }

    suspend fun updateProgress(documentId: String, page: Int, totalPages: Int) {
        val progress = if (totalPages > 0) ((page + 1).toFloat() / totalPages) * 100 else 0f
        documentDao.updateProgress(
            id = documentId,
            timestamp = System.currentTimeMillis(),
            page = page,
            total = totalPages,
            progress = progress.coerceIn(0f, 100f)
        )
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document)
    }

    suspend fun updateVoicePosition(documentId: String, sentenceIndex: Int) {
        documentDao.updateVoicePosition(documentId, sentenceIndex)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result = "Unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) result = cursor.getString(nameIndex)
            }
        }
        return result
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var result = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) result = cursor.getLong(sizeIndex)
            }
        }
        return result
    }

    private fun getDocumentType(uri: Uri): DocumentType {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        return when {
            fileName.endsWith(".pdf") -> DocumentType.PDF
            fileName.endsWith(".txt") -> DocumentType.TXT
            fileName.endsWith(".epub") -> DocumentType.EPUB
            else -> DocumentType.PDF
        }
    }

    suspend fun clearAllProgress() = documentDao.clearAllProgress()
}
