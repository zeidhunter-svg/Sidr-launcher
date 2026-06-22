package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.AppUsageEntity
import com.sidr.launcher.domain.history.AppUsageRecord

internal object AppUsageMapper {
    fun toDomain(entity: AppUsageEntity): AppUsageRecord = AppUsageRecord(
        packageName = entity.packageName,
        lastUsedEpochMs = entity.lastUsedEpochMs,
        launchCount = entity.launchCount,
    )
}
