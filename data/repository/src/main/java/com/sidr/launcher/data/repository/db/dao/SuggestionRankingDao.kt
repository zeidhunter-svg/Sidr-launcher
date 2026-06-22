package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionRankingDao {

    @Query("SELECT * FROM suggestion_ranking ORDER BY score DESC")
    fun getAllOrderedByScore(): Flow<List<SuggestionRankingEntity>>

    @Upsert
    suspend fun upsert(entity: SuggestionRankingEntity)

    @Query("SELECT COUNT(*) FROM suggestion_ranking")
    suspend fun count(): Int

    @Query(
        "DELETE FROM suggestion_ranking WHERE action_id IN " +
            "(SELECT action_id FROM suggestion_ranking ORDER BY score ASC LIMIT :excess)"
    )
    suspend fun deleteLowestScored(excess: Int)
}
