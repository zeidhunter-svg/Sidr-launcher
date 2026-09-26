package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "resolution_preferences", primaryKeys = ["action_id", "query", "context_key"])
data class ResolutionPreferenceEntity(
    @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "context_key") val contextKey: String,   // v1 always "none"
    @ColumnInfo(name = "preferred_target_type") val preferredTargetType: String,   // "app"
    @ColumnInfo(name = "preferred_target_value") val preferredTargetValue: String, // packageName
    @ColumnInfo(name = "streak") val streak: Int,
    @ColumnInfo(name = "total_choices") val totalChoices: Int,
    @ColumnInfo(name = "last_chosen_at") val lastChosenAtEpochMs: Long,
    @ColumnInfo(name = "learned_in_fingerprint") val learnedInFingerprint: String,
)
