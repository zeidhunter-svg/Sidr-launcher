package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResolutionPreferenceDao {
    @Query("SELECT * FROM resolution_preferences WHERE action_id = :actionId AND query = :query AND context_key = :contextKey LIMIT 1")
    suspend fun findByKey(actionId: String, query: String, contextKey: String): ResolutionPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResolutionPreferenceEntity)

    @Query("DELETE FROM resolution_preferences WHERE action_id = :actionId AND query = :query AND context_key = :contextKey")
    suspend fun deleteByKey(actionId: String, query: String, contextKey: String)

    @Query("DELETE FROM resolution_preferences WHERE preferred_target_value = :packageName")
    suspend fun deleteByTargetValue(packageName: String)

    @Query("SELECT * FROM resolution_preferences ORDER BY last_chosen_at DESC")
    fun observeAll(): Flow<List<ResolutionPreferenceEntity>>

    @Query("SELECT COUNT(*) FROM resolution_preferences")
    suspend fun count(): Int

    @Query("DELETE FROM resolution_preferences WHERE (action_id, query, context_key) IN " +
        "(SELECT action_id, query, context_key FROM resolution_preferences ORDER BY last_chosen_at ASC LIMIT :excess)")
    suspend fun deleteOldest(excess: Int)
}
