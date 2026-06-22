package com.sidr.launcher.domain.history

/**
 * One intent-match event. Carries only structural metadata — no raw voice, no free-text
 * AI dialogue, no location/calendar context (Fork 3).
 *
 * [normalizedText] for [IntentMatchType.SEARCH] matches is redacted to a verb-only placeholder
 * (e.g. "search") by the data layer before persistence; the domain model itself is a clean
 * data holder and does not enforce this policy.
 */
data class IntentMatchRecord(
    val normalizedText: String,
    val matchType: IntentMatchType,
    val confidence: Double,
    val timestampEpochMs: Long,
)
