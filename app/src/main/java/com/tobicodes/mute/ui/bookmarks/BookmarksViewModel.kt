package com.tobicodes.mute.ui.bookmarks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tobicodes.mute.MuteApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as MuteApplication
    
    val items: StateFlow<List<BookmarkItem>> = combine(
        app.bookmarkRepository.getAllBookmarks(),
        app.documentRepository.getAllDocuments()
    ) { bookmarks, docs ->
        val titles = docs.associate { it.id to it.title }
        bookmarks.mapNotNull { b ->
            titles[b.documentId]?.let { BookmarkItem(b, it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun delete(item: BookmarkItem) {
        viewModelScope.launch { app.bookmarkRepository.deleteBookmark(item.bookmark) }
    }
}
