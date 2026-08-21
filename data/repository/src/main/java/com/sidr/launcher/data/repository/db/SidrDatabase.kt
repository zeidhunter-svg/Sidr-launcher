package com.sidr.launcher.data.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sidr.launcher.data.repository.db.converter.IntentMatchTypeConverter
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.data.repository.db.dao.AliasDao
import com.sidr.launcher.data.repository.db.dao.AppUsageDao
import com.sidr.launcher.data.repository.db.dao.IntentMatchDao
import com.sidr.launcher.data.repository.db.dao.ResolutionPreferenceDao
import com.sidr.launcher.data.repository.db.dao.SuggestionRankingDao
import com.sidr.launcher.data.repository.db.entity.AgentPlanStepEntity
import com.sidr.launcher.data.repository.db.entity.AgentSessionEntity
import com.sidr.launcher.data.repository.db.entity.AgentTraceEventEntity
import com.sidr.launcher.data.repository.db.entity.AliasEntity
import com.sidr.launcher.data.repository.db.entity.AppUsageEntity
import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity

/**
 * Room database for Block F history tables (Fork 2), the Stage-2 S2-1 `resolution_preferences`
 * table, the Stage-2 S2-2 `aliases` table, and the agentic-track A0 session tables.
 *
 * - `version = 4`, `exportSchema = true` → schema JSON committed under `data/repository/schemas/`.
 *   `1.json` is the frozen historical 3-entity shape; `2.json` (Task 9) adds `resolution_preferences`
 *   via `Migration1To2`; `3.json` adds `aliases` via `Migration2To3`; `4.json` (A0 Task 10) adds the
 *   three `agent_*` tables via `Migration3To4`.
 * - The five older tables are recreatable learning/cache tables. `fallbackToDestructiveMigration` may be
 *   enabled by the DI builder (F7) **only for debug builds**; release builds get no destructive
 *   fallback, so a missing migration fails loudly instead of silently wiping data.
 * - When an entity changes: bump `version`, add a `Migration` under `db/migrations/`, commit the new
 *   golden schema, and cover it in the instrumented `MigrationTest`.
 */
@Database(
    entities = [
        AppUsageEntity::class,
        SuggestionRankingEntity::class,
        IntentMatchEntity::class,
        // Stage-2 S2-1 Task 7: registered so the DAO is reachable for testing. Task 9 bumped
        // `version` to 2, added `Migration1To2`, and committed the regenerated golden schema
        // (schemas/2.json) — schemas/1.json stays frozen as the historical 3-entity v1 shape.
        // Nothing consumes this table yet (deferred to Phase C wiring).
        ResolutionPreferenceEntity::class,
        // Stage-2 S2-2: explicit user-authored aliases, added in schema v3 via Migration2To3.
        AliasEntity::class,
        // Agentic track A0 Task 10: the one in-flight agent session, added in schema v4 via
        // Migration3To4. Not a journal — any terminal state deletes the session and the two child
        // tables follow by cascade, so at rest all three are empty (A0 spec §7).
        AgentSessionEntity::class,
        AgentPlanStepEntity::class,
        AgentTraceEventEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(IntentMatchTypeConverter::class)
abstract class SidrDatabase : RoomDatabase() {
    abstract fun appUsageDao(): AppUsageDao
    abstract fun suggestionRankingDao(): SuggestionRankingDao
    abstract fun intentMatchDao(): IntentMatchDao
    abstract fun resolutionPreferenceDao(): ResolutionPreferenceDao
    abstract fun aliasDao(): AliasDao
    abstract fun agentSessionDao(): AgentSessionDao

    companion object {
        const val DATABASE_NAME = "sidr_history.db"
    }
}
