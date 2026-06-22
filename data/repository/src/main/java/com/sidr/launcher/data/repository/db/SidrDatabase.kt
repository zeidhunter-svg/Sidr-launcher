package com.sidr.launcher.data.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sidr.launcher.data.repository.db.converter.IntentMatchTypeConverter
import com.sidr.launcher.data.repository.db.dao.AppUsageDao
import com.sidr.launcher.data.repository.db.dao.IntentMatchDao
import com.sidr.launcher.data.repository.db.dao.SuggestionRankingDao
import com.sidr.launcher.data.repository.db.entity.AppUsageEntity
import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity

/**
 * Room database for Block F history tables (Fork 2).
 *
 * - `version = 1`, `exportSchema = true` → schema JSON committed under `data/repository/schemas/`.
 * - All three tables are recreatable learning/cache tables. `fallbackToDestructiveMigration` may be
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(IntentMatchTypeConverter::class)
abstract class SidrDatabase : RoomDatabase() {
    abstract fun appUsageDao(): AppUsageDao
    abstract fun suggestionRankingDao(): SuggestionRankingDao
    abstract fun intentMatchDao(): IntentMatchDao

    companion object {
        const val DATABASE_NAME = "sidr_history.db"
    }
}
