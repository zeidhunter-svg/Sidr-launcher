package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One trace event, as **flat columns rather than a JSON blob** — so A5 ("trace as a surface") can ask
 * for "the last N events" without converting a format first.
 *
 * [detail] is the single extra value the variant carries: a reason name, a tool id, a precondition
 * fact, a granted flag, a state name, a step count — or `null` for the variants that carry none.
 * `ToolObserved` is the one variant whose payload is **not** repeated here: the result already lives on
 * the step row's observation columns, and duplicating it would create two places for one fact to be
 * stored and one way for them to disagree.
 *
 * [at] is stamped in this layer and preserved per `(session_id, seq)` across re-saves. The trace is
 * append-only, so an event's `seq` is its identity; restamping every row on every transition would
 * collapse the column to "when the session was last written".
 */
@Entity(
    tableName = "agent_trace_event",
    primaryKeys = ["session_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AgentTraceEventEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "seq") val seq: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int?,
    @ColumnInfo(name = "detail") val detail: String?,
    @ColumnInfo(name = "at") val at: Long,
)
