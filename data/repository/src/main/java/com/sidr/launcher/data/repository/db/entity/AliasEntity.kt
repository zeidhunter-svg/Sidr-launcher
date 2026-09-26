package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "aliases", primaryKeys = ["phrase"])
data class AliasEntity(
    @ColumnInfo(name = "phrase") val phrase: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_package") val targetPackage: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
