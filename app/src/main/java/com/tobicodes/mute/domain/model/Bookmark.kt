package com.tobicodes.mute.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey
    val id: Long = System.currentTimeMillis(),
    val documentId: String,           // Foreign key to Document
    val page: Int,                     // Page number
    val previewText: String?,          // Optional preview
    val createdAt: Long = System.currentTimeMillis()
)
