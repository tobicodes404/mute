package com.tobicodes.mute.data.local.db

import androidx.room.TypeConverter
import com.tobicodes.mute.domain.model.DocumentType

class Converters {
    @TypeConverter
    fun fromDocumentType(type: DocumentType): String = type.name

    @TypeConverter
    fun toDocumentType(value: String): DocumentType = DocumentType.valueOf(value)
}
