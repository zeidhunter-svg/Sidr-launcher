package com.sidr.launcher.data.repository.db.converter

import androidx.room.TypeConverter
import com.sidr.launcher.domain.history.IntentMatchType

class IntentMatchTypeConverter {
    @TypeConverter
    fun fromString(value: String): IntentMatchType =
        IntentMatchType.entries.firstOrNull { it.name == value } ?: IntentMatchType.UNKNOWN

    @TypeConverter
    fun toString(type: IntentMatchType): String = type.name
}
