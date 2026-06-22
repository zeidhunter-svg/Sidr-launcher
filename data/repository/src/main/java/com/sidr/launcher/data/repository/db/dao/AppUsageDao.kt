package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sidr.launcher.data.repository.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppUsageDao {

    @Query("SELECT * FROM app_usage ORDER BY launch_count DESC, last_used_epoch_ms DESC")
    abstract fun getAllOrderedByUsage(): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE package_name = :packageName LIMIT 1")
    abstract suspend fun getByPackageName(packageName: String): AppUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: AppUsageEntity)

    @Query(
        "UPDATE app_usage SET last_used_epoch_ms = :timestampMs, launch_count = :newCount " +
            "WHERE package_name = :packageName"
    )
    abstract suspend fun updateLaunch(packageName: String, timestampMs: Long, newCount: Int)

    @Query("SELECT COUNT(*) FROM app_usage")
    abstract suspend fun count(): Int

    @Query(
        "DELETE FROM app_usage WHERE package_name IN " +
            "(SELECT package_name FROM app_usage ORDER BY last_used_epoch_ms ASC LIMIT :excess)"
    )
    abstract suspend fun deleteOldest(excess: Int)

    /** Increments launch_count for an existing record, or inserts a new one (count = 1). */
    @Transaction
    open suspend fun upsertLaunch(packageName: String, timestampMs: Long) {
        val existing = getByPackageName(packageName)
        if (existing != null) {
            updateLaunch(packageName, timestampMs, existing.launchCount + 1)
        } else {
            insert(AppUsageEntity(packageName = packageName, lastUsedEpochMs = timestampMs, launchCount = 1))
        }
    }
}
