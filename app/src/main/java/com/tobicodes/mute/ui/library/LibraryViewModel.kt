package com.tobicodes.mute.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tobicodes.mute.MuteApplication
import com.tobicodes.mute.domain.model.Document
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as MuteApplication).documentRepository
    
    val documents: StateFlow<List<Document>> = repository.getAllDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.addDocument(getApplication(), uri)
            } catch (e: Exception) {
                e.printStackTrace()
                // Future: Show error to user
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }
}
