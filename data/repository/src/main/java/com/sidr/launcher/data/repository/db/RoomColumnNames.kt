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
 *
 * Stage-2 S2-1 Task 9 adds [RESOLUTION_PREFERENCES] + [BY_TABLE] + [APPROVED_SENSITIVE_COLUMNS]:
 * `resolution_preferences.query` collides with the forbidden term `"query"`, but per the S2-1
 * spec (§7) that column stores **normalized slot metadata only** (e.g. `"telegram"`, not the raw
 * user command), is local-only, user-visible & deletable, and is never sent to cloud AI/LLM. The
 * owner decision is to keep the column name and grant a narrow, table+column-scoped exemption
 * rather than relax the guard globally (see [APPROVED_SENSITIVE_COLUMNS]).
 *
 * Stage-2 S2-2 adds [ALIASES]. Alias phrases and target packages are local-sensitive explicit
 * memory, but their column names do not collide with the forbidden term inventory, so no scoped
 * exemption is needed.
 *
 * Agentic-track A0 Task 10 adds [AGENT_SESSION], [AGENT_PLAN_STEP] and [AGENT_TRACE_EVENT]. These
 * carry the most command-shaped data in the database — `agent_session.goal_text` is the raw command —
 * but they are a **recovery record, not a journal**: any terminal state deletes the session and the
 * two child tables follow by cascade, so at rest all three are empty (A0 spec §7). The shape's argument
 * is `goal_shape_arg`: `query` is a forbidden term below, so the spec's original `goal_query` would
 * have forced a second entry in [APPROVED_SENSITIVE_COLUMNS]. The spec was amended to match on
 * 2026-08-21, so this block adds **no** new entry — the S2-1 exemption stays the only one in the
 * database, and it stays owner-granted.
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

    /**
     * [com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity] — table:
     * resolution_preferences (Stage-2 S2-1 Task 7/9).
     *
     * `query` here is normalized slot text extracted from a command (e.g. `"telegram"`), never
     * the raw user command string; see [APPROVED_SENSITIVE_COLUMNS] for the scoped guard
     * exemption this requires.
     */
    val RESOLUTION_PREFERENCES: Set<String> = setOf(
        "action_id",
        "query",
        "context_key",
        "preferred_target_type",
        "preferred_target_value",
        "streak",
        "total_choices",
        "last_chosen_at",
        "learned_in_fingerprint",
    )

    /** [com.sidr.launcher.data.repository.db.entity.AliasEntity] — table: aliases (Stage-2 S2-2). */
    val ALIASES: Set<String> = setOf(
        "phrase",
        "target_type",
        "target_package",
        "created_at",
    )

    /**
     * [com.sidr.launcher.data.repository.db.entity.AgentSessionEntity] — table: agent_session
     * (agentic track A0 Task 10).
     *
     * `goal_shape_arg` holds the single argument of `goal_shape` — for `AppNotInstalled`, the app name
     * the command named. Named that way so no scoped exemption is needed; do not rename it to anything
     * containing a forbidden term. `goal_text` holds the raw command and is deleted with the session on
     * any terminal state.
     */
    val AGENT_SESSION: Set<String> = setOf(
        "id",
        "goal_text",
        "goal_shape",
        "goal_shape_arg",
        "state",
        "cursor",
        "created_at",
    )

    /** [com.sidr.launcher.data.repository.db.entity.AgentPlanStepEntity] — table: agent_plan_step. */
    val AGENT_PLAN_STEP: Set<String> = setOf(
        "session_id",
        "step_index",
        "tool_id",
        "args_json",
        "risk",
        "precondition_fact",
        "rationale",
        "observation_type",
        "observation_fact",
        "observation_output_json",
        "consent",
    )

    /** [com.sidr.launcher.data.repository.db.entity.AgentTraceEventEntity] — table: agent_trace_event. */
    val AGENT_TRACE_EVENT: Set<String> = setOf(
        "session_id",
        "seq",
        "type",
        "step_index",
        "detail",
        "at",
    )

    val ALL: Set<String> = APP_USAGE + SUGGESTION_RANKING + INTENT_MATCH + RESOLUTION_PREFERENCES +
        ALIASES + AGENT_SESSION + AGENT_PLAN_STEP + AGENT_TRACE_EVENT

    /**
     * Every `@Entity(tableName = ...)` in [SidrDatabase]. Kept in sync by hand with the entity
     * annotations; the migration golden-schema check (androidTest) is the structural backstop.
     */
    val TABLE_NAMES: Set<String> = setOf(
        "app_usage",              // AppUsageEntity
        "suggestion_ranking",     // SuggestionRankingEntity
        "intent_match",           // IntentMatchEntity
        "resolution_preferences", // ResolutionPreferenceEntity
        "aliases",                // AliasEntity
        "agent_session",          // AgentSessionEntity      (A0 Task 10)
        "agent_plan_step",        // AgentPlanStepEntity     (A0 Task 10)
        "agent_trace_event",      // AgentTraceEventEntity   (A0 Task 10)
    )

    /**
     * Table -> its column set, for the guard's table-aware scan (Task 9). Lets the guard resolve
     * each column's fully-qualified `table.column` name so [APPROVED_SENSITIVE_COLUMNS] can be
     * scoped to a specific table's column rather than a bare column-name allow-list (which would
     * silently exempt e.g. a hypothetical `other_table.query` too).
     */
    val BY_TABLE: Map<String, Set<String>> = mapOf(
        "app_usage" to APP_USAGE,
        "suggestion_ranking" to SUGGESTION_RANKING,
        "intent_match" to INTENT_MATCH,
        "resolution_preferences" to RESOLUTION_PREFERENCES,
        "aliases" to ALIASES,
        "agent_session" to AGENT_SESSION,
        "agent_plan_step" to AGENT_PLAN_STEP,
        "agent_trace_event" to AGENT_TRACE_EVENT,
    )

    /**
     * Narrow, documented exemptions from the forbidden-term scan, keyed by fully-qualified
     * `table.column`. **Not** a bare column-name allow-list — a column is only skipped if its
     * exact `table.column` string is present here, so an unrelated table's same-named column
     * (e.g. `other_table.query`) is still flagged.
     *
     * `resolution_preferences.query`: stores **normalized slot metadata only** (not the raw user
     * command), is local-only, user-visible & deletable via the Task-8 store, and is never sent
     * to cloud AI/LLM (S2-1 spec §7). The column name is kept as-is (matches the domain model);
     * the guard is narrowed instead of relaxed globally.
     */
    val APPROVED_SENSITIVE_COLUMNS: Set<String> = setOf(
        "resolution_preferences.query",
    )
}
