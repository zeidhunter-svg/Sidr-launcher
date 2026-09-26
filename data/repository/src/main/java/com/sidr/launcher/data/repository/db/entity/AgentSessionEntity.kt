package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The **one** agent session in flight (A0 spec §7). Zero or one row: any terminal state deletes it,
 * and [com.sidr.launcher.data.repository.db.dao.AgentSessionDao.replaceSession] removes any other id
 * as part of writing this one, so "0 or 1" is a property of the write path rather than a convention.
 *
 * `goal_shape` is a discriminator column although A0 had exactly one shape, so that a second shape is
 * **additive** — a new value in this column's vocabulary — rather than a silent reinterpretation of
 * rows already on disk. A1' Task 9's `Free` is the first payment on that: the mapper gained an encode
 * arm and a decode arm, existing `AppNotInstalled` rows still read as themselves, and the table's SQL
 * and `identity_hash` did not move, so no migration was involved. `goal_shape_arg` carries that
 * shape's single argument — for `AppNotInstalled`, the app name the command named; for `Free`, the
 * raw command text (see `AgentSessionMappers.toSessionEntity` for why it is duplicated with
 * `goal_text` rather than rebuilt from it).
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
