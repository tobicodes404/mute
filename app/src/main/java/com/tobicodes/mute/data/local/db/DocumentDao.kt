package com.tobicodes.mute.data.local.db

import androidx.room.*
import com.tobicodes.mute.domain.model.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document)

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("UPDATE documents SET lastOpenedAt = :timestamp, currentPage = :page, totalPages = :total, progressPercentage = :progress WHERE id = :id")
    suspend fun updateProgress(id: String, timestamp: Long, page: Int, total: Int, progress: Float)

    @Query("UPDATE documents SET lastSentenceIndex = :sentenceIndex WHERE id = :id")
    suspend fun updateVoicePosition(id: String, sentenceIndex: Int)

    @Query("UPDATE documents SET currentPage = 0, lastSentenceIndex = 0")
    suspend fun clearAllProgress()
}
