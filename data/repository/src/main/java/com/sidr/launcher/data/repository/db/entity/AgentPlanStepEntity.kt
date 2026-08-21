package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One planned step, its observation, and its consent decision.
 *
 * **[argsJson] and [observationOutputJson] are the two halves of one F6 pair.** [argsJson] stores
 * `ArgSource` — a `Literal`'s string, or a `FromStep`'s index and key — and **never** resolved values:
 * a plan that outlived its process must re-bind against the observations that actually survived, not
 * replay a value frozen when the plan was written. [observationOutputJson] is the producing side.
 *
 * A step that produced nothing stores `null` there, not `{}`, so "produced nothing" and "produced an
 * empty map" stay distinguishable — `AgentSessionDaoTest` asserts that at the SQL level.
 *
 * [consent] is `null` until a decision is recorded, which is exactly what
 * [com.sidr.launcher.data.repository.db.dao.AgentSessionDao.recordConsentIfPending] tests for. It is a
 * three-valued column on purpose: "not asked yet", "granted" and "refused" are three different facts.
 *
 * `session_id` is the leftmost column of the primary key, so the composite index already covers the
 * foreign key and no separate `@Index` is needed.
 */
@Entity(
    tableName = "agent_plan_step",
    primaryKeys = ["session_id", "step_index"],
    foreignKeys = [
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AgentPlanStepEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "tool_id") val toolId: String,
    @ColumnInfo(name = "args_json") val argsJson: String,
    @ColumnInfo(name = "risk") val risk: String,
    @ColumnInfo(name = "precondition_fact") val preconditionFact: String?,
    @ColumnInfo(name = "rationale") val rationale: String,
    @ColumnInfo(name = "observation_type") val observationType: String?,
    @ColumnInfo(name = "observation_fact") val observationFact: String?,
    @ColumnInfo(name = "observation_output_json") val observationOutputJson: String?,
    @ColumnInfo(name = "consent") val consent: Boolean?,
)
