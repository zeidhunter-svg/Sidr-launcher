package com.sidr.launcher.data.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sidr.launcher.data.repository.db.converter.IntentMatchTypeConverter
import com.sidr.launcher.data.repository.db.dao.AppUsageDao
import com.sidr.launcher.data.repository.db.dao.IntentMatchDao
import com.sidr.launcher.data.repository.db.dao.ResolutionPreferenceDao
import com.sidr.launcher.data.repository.db.dao.SuggestionRankingDao
import com.sidr.launcher.data.repository.db.entity.AppUsageEntity
import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity

/**
 * Room database for Block F history tables (Fork 2) + the Stage-2 S2-1 `resolution_preferences`
 * table (Task 7/9).
 *
 * - `version = 2`, `exportSchema = true` → schema JSON committed under `data/repository/schemas/`.
 *   `1.json` is the frozen historical 3-entity shape; `2.json` (Task 9) adds `resolution_preferences`
 *   via `Migration1To2`.
 * - All four tables are recreatable learning/cache tables. `fallbackToDestructiveMigration` may be
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
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(IntentMatchTypeConverter::class)
abstract class SidrDatabase : RoomDatabase() {
    abstract fun appUsageDao(): AppUsageDao
    abstract fun suggestionRankingDao(): SuggestionRankingDao
    abstract fun intentMatchDao(): IntentMatchDao
    abstract fun resolutionPreferenceDao(): ResolutionPreferenceDao

    companion object {
        const val DATABASE_NAME = "sidr_history.db"
    }
}
