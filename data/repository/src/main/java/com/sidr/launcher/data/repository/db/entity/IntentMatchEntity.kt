package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sidr.launcher.domain.history.IntentMatchType

@Entity(tableName = "intent_match")
data class IntentMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // For SEARCH matches this field is redacted to the verb prefix only ("search") by the
    // mapper (IntentMatchMapper) before the entity is written — query content is never stored.
    @ColumnInfo(name = "normalized_text")
    val normalizedText: String,
    @ColumnInfo(name = "match_type")
    val matchType: IntentMatchType,
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    @ColumnInfo(name = "timestamp_epoch_ms")
    val timestampEpochMs: Long,
)
