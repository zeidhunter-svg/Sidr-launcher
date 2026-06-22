package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.history.IntentMatchType

internal object IntentMatchMapper {

    // ─── PRIVACY — Fork 3 / Block F ──────────────────────────────────────────
    // Classification principle: redact every match type that carries ARBITRARY user content
    // (SEARCH = query argument, UNKNOWN = unrecognized free text); store only a placeholder for
    // those. Structurally-bounded types (LAUNCH_APP / OPEN_SETTINGS / SIMPLE_COMMAND) resolve to a
    // closed vocabulary and are kept as-is. New intent types must be classified by this rule.
    // This is the single enforcement point; the domain model (IntentMatchRecord) is unaware.
    private val REDACTED_PLACEHOLDER: Map<IntentMatchType, String> = mapOf(
        IntentMatchType.SEARCH to "search",
        IntentMatchType.UNKNOWN to "unknown",
    )
    // ─────────────────────────────────────────────────────────────────────────

    fun toEntity(record: IntentMatchRecord): IntentMatchEntity = IntentMatchEntity(
        normalizedText = REDACTED_PLACEHOLDER[record.matchType] ?: record.normalizedText,
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
