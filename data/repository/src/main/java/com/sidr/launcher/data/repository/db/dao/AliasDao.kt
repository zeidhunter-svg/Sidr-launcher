package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sidr.launcher.data.repository.db.entity.AliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases WHERE phrase = :phrase LIMIT 1")
    suspend fun findByPhrase(phrase: String): AliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AliasEntity)

    @Query("DELETE FROM aliases WHERE phrase = :phrase")
    suspend fun deleteByPhrase(phrase: String)

    @Query("DELETE FROM aliases WHERE target_package = :packageName")
    suspend fun deleteByTargetPackage(packageName: String)

    @Query("SELECT * FROM aliases ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AliasEntity>>
}
