package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity
import com.sidr.launcher.domain.history.SuggestionRankingRecord

internal object SuggestionRankingMapper {
    fun toEntity(record: SuggestionRankingRecord): SuggestionRankingEntity = SuggestionRankingEntity(
        actionId = record.actionId,
        label = record.label,
        score = record.score,
        lastUpdatedEpochMs = record.lastUpdatedEpochMs,
    )

    fun toDomain(entity: SuggestionRankingEntity): SuggestionRankingRecord = SuggestionRankingRecord(
        actionId = entity.actionId,
        label = entity.label,
        score = entity.score,
        lastUpdatedEpochMs = entity.lastUpdatedEpochMs,
    )
}
