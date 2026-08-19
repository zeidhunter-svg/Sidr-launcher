package com.sidr.launcher.domain.history

data class AppUsageRecord(
    val packageName: String,
    val lastUsedEpochMs: Long,
    val launchCount: Int,
)
