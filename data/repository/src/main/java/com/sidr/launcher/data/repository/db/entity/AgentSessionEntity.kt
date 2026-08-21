package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The **one** agent session in flight (A0 spec §7). Zero or one row: any terminal state deletes it,
 * and [com.sidr.launcher.data.repository.db.dao.AgentSessionDao.replaceSession] removes any other id
 * as part of writing this one, so "0 or 1" is a property of the write path rather than a convention.
 *
 * `goal_shape` is a column although A0 has exactly one shape: adding a second shape must then be a
 * migration, not a silent reinterpretation of rows already on disk. `goal_shape_arg` carries that
 * shape's single argument — for `AppNotInstalled`, the app name the command named.
 *
 * `created_at` is stamped in this layer (the domain has no clock, deliberately) and is **preserved**
 * across re-saves of the same session; a column that restamped on every transition would say "created"
 * and mean "last written".
 */
@Entity(tableName = "agent_session", primaryKeys = ["id"])
data class AgentSessionEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "goal_text") val goalText: String,
    @ColumnInfo(name = "goal_shape") val goalShape: String,
    @ColumnInfo(name = "goal_shape_arg") val goalShapeArg: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "cursor") val cursor: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
