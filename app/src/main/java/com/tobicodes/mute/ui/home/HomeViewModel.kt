package com.tobicodes.mute.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tobicodes.mute.MuteApplication
import com.tobicodes.mute.domain.model.Document
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as MuteApplication).documentRepository
    
    val recentDocument: StateFlow<Document?> = repository.getAllDocuments()
        .map { docs -> docs.filter { it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt ?: 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val recentDocuments: StateFlow<List<Document>> = repository.getAllDocuments()
        .map { docs -> docs.filter { it.lastOpenedAt != null }.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val stats: StateFlow<LibraryStats> = repository.getAllDocuments()
        .map { docs ->
            LibraryStats(
                totalDocuments = docs.size,
                totalCompleted = docs.count { it.progressPercentage >= 95f },
                totalInProgress = docs.count { it.progressPercentage in 1f..94f }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())
    
    fun deleteDocument(document: Document) {
        viewModelScope.launch { repository.deleteDocument(document) }
    }
}

data class LibraryStats(
    val totalDocuments: Int = 0,
    val totalCompleted: Int = 0,
    val totalInProgress: Int = 0
)
