package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntentMatchDao {

    @Query("SELECT * FROM intent_match ORDER BY timestamp_epoch_ms DESC")
    fun getAllOrderedByRecency(): Flow<List<IntentMatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IntentMatchEntity)

    @Query("SELECT COUNT(*) FROM intent_match")
    suspend fun count(): Int

    @Query(
        "DELETE FROM intent_match WHERE id IN " +
            "(SELECT id FROM intent_match ORDER BY timestamp_epoch_ms ASC LIMIT :excess)"
    )
    suspend fun deleteOldest(excess: Int)
}
