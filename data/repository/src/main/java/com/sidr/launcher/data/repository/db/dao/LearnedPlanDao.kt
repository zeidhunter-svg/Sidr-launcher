package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sidr.launcher.data.repository.db.entity.LearnedPlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * The `learned_plans` table (A4′ phase 4). One row per form ([LearnedPlanEntity.form] is the primary key).
 * The cap and its eviction order (plan §0.2 B-9) are applied by [upsertCapped], in one transaction with the
 * write, so no reader ever sees the table past its cap.
 */
@Dao
abstract class LearnedPlanDao {

    @Query("SELECT * FROM learned_plans WHERE form = :form LIMIT 1")
    abstract suspend fun find(form: String): LearnedPlanEntity?

    @Query("SELECT * FROM learned_plans ORDER BY confirmed_at DESC, form ASC")
    abstract suspend fun all(): List<LearnedPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: LearnedPlanEntity)

    @Query("DELETE FROM learned_plans WHERE form = :form")
    abstract suspend fun delete(form: String)

    @Query("SELECT * FROM learned_plans ORDER BY confirmed_at DESC, form ASC")
    abstract fun observeAll(): Flow<List<LearnedPlanEntity>>

    @Query("SELECT COUNT(*) FROM learned_plans")
    abstract suspend fun count(): Int

    /**
     * Deletes [excess] rows other than [keep], in B-9's order: **unpinned rows first** (a streak below
     * [pinnedAt]) — the lowest streak, then the oldest confirmation; **a pinned row only when no unpinned one is
     * left**, the oldest confirmation first whatever its streak (a replay writes nothing, so a command used every
     * day keeps the confirmation time of its third reading — review S1). `form` breaks a tie so the order is
     * total. [keep] is the row just written: a fresh reading is never evicted by its own write.
     */
    @Query(
        """
        DELETE FROM learned_plans WHERE form IN (
            SELECT form FROM learned_plans
            WHERE form != :keep
            ORDER BY (streak >= :pinnedAt) ASC,
                     CASE WHEN streak >= :pinnedAt THEN 0 ELSE streak END ASC,
                     confirmed_at ASC,
                     form ASC
            LIMIT :excess
        )
        """,
    )
    abstract suspend fun evict(excess: Int, keep: String, pinnedAt: Int)

    /** [upsert], then at most [cap] rows are left — [evict]'s order — in one transaction. */
    @Transaction
    open suspend fun upsertCapped(entity: LearnedPlanEntity, cap: Int, pinnedAt: Int) {
        upsert(entity)
        val excess = count() - cap
        if (excess > 0) evict(excess, keep = entity.form, pinnedAt = pinnedAt)
    }
}
