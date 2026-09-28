package com.tobicodes.mute.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey
    val id: String,                    // Unique ID (generated from URI)
    val title: String,                 // Display name
    val uriString: String,             // URI as string (for SAF)
    val type: DocumentType,            // PDF, TXT, EPUB
    val sizeBytes: Long,               // File size
    val createdAt: Long,               // When added to library
    val lastOpenedAt: Long? = null,    // Last time opened
    val currentPage: Int = 0,          // Current reading position
    val totalPages: Int = 0,           // Total pages
    val progressPercentage: Float = 0f, // Reading progress 0-100
    val lastSentenceIndex: Int = -1    // Last played sentence (-1 = not started)
)

enum class DocumentType {
    PDF, TXT, EPUB
}
