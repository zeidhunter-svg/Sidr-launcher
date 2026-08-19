package com.sidr.launcher.domain.history

data class SuggestionRankingRecord(
    val actionId: String,
    val label: String,
    val score: Double,
    val lastUpdatedEpochMs: Long,
)
