package com.sidr.launcher.data.repository.db

/**
 * All Room column names across every entity in [SidrDatabase].
 *
 * Used exclusively by the privacy guard test (Block F, step F8) to assert that no column name
 * carries a forbidden term — mirroring the DataStore key guard in [PreferencesKeys.ALL_KEY_NAMES]
 * (Block E). Structural table-metadata names (e.g. "id") are included so the guard covers
 * the full persisted schema surface.
 */
internal object RoomColumnNames {

    /** [com.sidr.launcher.data.repository.db.entity.AppUsageEntity] — table: app_usage */
    val APP_USAGE: Set<String> = setOf(
        "package_name",
        "last_used_epoch_ms",
        "launch_count",
    )

    /** [com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity] — table: suggestion_ranking */
    val SUGGESTION_RANKING: Set<String> = setOf(
        "action_id",
        "label",
        "score",
        "last_updated_epoch_ms",
    )

    /** [com.sidr.launcher.data.repository.db.entity.IntentMatchEntity] — table: intent_match */
    val INTENT_MATCH: Set<String> = setOf(
        "id",
        "normalized_text",
        "match_type",
        "confidence",
        "timestamp_epoch_ms",
    )

    val ALL: Set<String> = APP_USAGE + SUGGESTION_RANKING + INTENT_MATCH
}
