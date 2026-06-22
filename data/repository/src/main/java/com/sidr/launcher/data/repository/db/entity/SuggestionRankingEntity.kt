package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggestion_ranking")
data class SuggestionRankingEntity(
    @PrimaryKey
    @ColumnInfo(name = "action_id")
    val actionId: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "score")
    val score: Double,
    @ColumnInfo(name = "last_updated_epoch_ms")
    val lastUpdatedEpochMs: Long,
)
