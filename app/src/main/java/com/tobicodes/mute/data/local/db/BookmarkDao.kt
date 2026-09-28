package com.tobicodes.mute.data.local.db

import androidx.room.*
import com.tobicodes.mute.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY page ASC")
    fun getBookmarksForDocument(documentId: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId AND page = :page LIMIT 1")
    suspend fun getBookmark(documentId: String, page: Int): Bookmark?

    @Insert
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)
}
