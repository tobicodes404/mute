package com.tobicodes.mute

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tobicodes.mute.data.local.db.AppDatabase
import com.tobicodes.mute.data.repository.BookmarkRepository
import com.tobicodes.mute.data.repository.DocumentRepository

class MuteApplication : Application() {
    
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    
    val documentRepository: DocumentRepository by lazy { DocumentRepository(database) }
    
    val bookmarkRepository: BookmarkRepository by lazy { BookmarkRepository(database) }
    
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}
