package com.sidr.launcher.data.repository.db

/**
 * All Room column **and table** names across every entity in [SidrDatabase].
 *
 * Used exclusively by the privacy guard test (Block F step F8; table-name scan added Block H
 * step H-a) to assert that no persisted identifier carries a forbidden term — mirroring the
 * DataStore key guard in [PreferencesKeys.ALL_KEY_NAMES] (Block E). Structural table-metadata
 * names (e.g. "id") are included so the guard covers the full persisted schema surface.
 *
 * Block F originally exempted table names as "structural identifiers"; Block H reclassifies them
 * as part of the persisted surface (a careless table like `voice_history` is just as much a leak
 * signal as a column), so [TABLE_NAMES] is now guarded alongside [ALL].
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

    /**
     * Every `@Entity(tableName = ...)` in [SidrDatabase]. Kept in sync by hand with the entity
     * annotations; the migration golden-schema check (androidTest) is the structural backstop.
     */
    val TABLE_NAMES: Set<String> = setOf(
        "app_usage",          // AppUsageEntity
        "suggestion_ranking", // SuggestionRankingEntity
        "intent_match",       // IntentMatchEntity
    )
}
