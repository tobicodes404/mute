package com.tobicodes.mute.data.repository

import com.tobicodes.mute.data.local.db.AppDatabase
import com.tobicodes.mute.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val database: AppDatabase) {
    
    private val bookmarkDao = database.bookmarkDao()

    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    fun getBookmarksForDocument(documentId: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForDocument(documentId)

    suspend fun getBookmark(documentId: String, page: Int): Bookmark? =
        bookmarkDao.getBookmark(documentId, page)

    /** Returns true if bookmark was added, false if removed */
    suspend fun toggleBookmark(documentId: String, page: Int, preview: String?): Boolean {
        val existing = bookmarkDao.getBookmark(documentId, page)
        return if (existing != null) {
            bookmarkDao.deleteBookmark(existing)
            false
        } else {
            bookmarkDao.insertBookmark(
                Bookmark(documentId = documentId, page = page, previewText = preview)
            )
            true
        }
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.deleteBookmark(bookmark)
}
