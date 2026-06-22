package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "last_used_epoch_ms")
    val lastUsedEpochMs: Long,
    @ColumnInfo(name = "launch_count")
    val launchCount: Int,
)
