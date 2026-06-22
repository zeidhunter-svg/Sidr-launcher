package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.history.IntentMatchType

internal object IntentMatchMapper {

    // ─── PRIVACY — Fork 3 / Block F ──────────────────────────────────────────
    // For SEARCH matches, normalizedText contains the user's query argument (e.g. "search cats").
    // We store ONLY the verb prefix — query content is never written to the database.
    // For all other match types, normalizedText is structural (e.g. "open telegram") and safe.
    // This is the single enforcement point; the domain model (IntentMatchRecord) is unaware.
    private const val SEARCH_REDACTED = "search"
    // ─────────────────────────────────────────────────────────────────────────

    fun toEntity(record: IntentMatchRecord): IntentMatchEntity = IntentMatchEntity(
        normalizedText = if (record.matchType == IntentMatchType.SEARCH) {
            SEARCH_REDACTED
        } else {
            record.normalizedText
        },
        matchType = record.matchType,
        confidence = record.confidence,
        timestampEpochMs = record.timestampEpochMs,
    )

    fun toDomain(entity: IntentMatchEntity): IntentMatchRecord = IntentMatchRecord(
        normalizedText = entity.normalizedText,
        matchType = entity.matchType,
        confidence = entity.confidence,
        timestampEpochMs = entity.timestampEpochMs,
    )
}
