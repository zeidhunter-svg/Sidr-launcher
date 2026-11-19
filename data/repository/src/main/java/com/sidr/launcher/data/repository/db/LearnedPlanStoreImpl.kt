package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.LearnedPlanDao
import com.sidr.launcher.data.repository.db.entity.LearnedPlanEntity
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.LearnedPlanStore
import com.sidr.launcher.domain.memory.plan.MAX_LEARNED_PLANS
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Room-backed [LearnedPlanStore] (A4′ phase 4, spec §8; plan §0.2 B-9): one `learned_plans` row per form.
 *
 * **It never throws** (rethrows [CancellationException] only). Step 2c's replay catches nothing and sits above
 * the local-only gate, so a throw here would end the command path; every I/O, SQLite and decode error of a
 * suspend operation is an [OperationResult.Failure], which the replay reads as a miss (B-5). [observeAll] is the
 * exception: a stream's failure surfaces as the flow's exception, for the screen to say so (Task 8).
 *
 * **The cap** — at most [MAX_LEARNED_PLANS] rows — is applied in [upsert], in the write's own transaction
 * ([LearnedPlanDao.upsertCapped]): unpinned rows go first, the lowest streak then the oldest confirmation; a pinned
 * row (streak ≥ [DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD], the threshold `LearnedPlan.pinned` reads) only when no
 * unpinned one is left, the oldest confirmation first; never the row just written.
 *
 * **A row whose template cannot be read is deleted when it is met** — by [find], [all] or [observeAll] — and left
 * out: it can never replay, and a row the screen cannot show would break `DOC-AML-5` (review M3; the precedent is
 * `PruneUnavailableLearnedChoicesUseCase`). "Cannot be read" is exactly the decoder's documented refusal (an
 * [IllegalArgumentException], `SerializationException` included) or a template with no step; any other error is a
 * Failure and deletes nothing, so a fault in this code cannot erase what the user taught.
 */
class LearnedPlanStoreImpl @Inject constructor(
    private val dao: LearnedPlanDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LearnedPlanStore {

    override suspend fun find(form: CommandForm): OperationResult<LearnedPlan?> =
        io("db_learned_plan_read_failed") { dao.find(form.key)?.let { readOrForget(it) } }

    override suspend fun all(): OperationResult<List<LearnedPlan>> =
        io("db_learned_plan_read_failed") { dao.all().mapNotNull { readOrForget(it) } }

    override suspend fun upsert(plan: LearnedPlan): OperationResult<Unit> =
        io("db_learned_plan_write_failed") {
            dao.upsertCapped(LearnedPlanRows.toEntity(plan), cap = MAX_LEARNED_PLANS, pinnedAt = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD)
        }

    override suspend fun delete(form: CommandForm): OperationResult<Unit> =
        io("db_learned_plan_delete_failed") { dao.delete(form.key) }

    override fun observeAll(): Flow<List<LearnedPlan>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { readOrForget(it) } }
            .flowOn(ioDispatcher)

    /** [row] as a plan, or — its template unreadable — deleted and `null` (B-9). */
    private suspend fun readOrForget(row: LearnedPlanEntity): LearnedPlan? =
        LearnedPlanRows.toDomain(row) ?: run {
            dao.delete(row.form)
            null
        }

    private suspend fun <T> io(reason: String, block: suspend () -> T): OperationResult<T> =
        withContext(ioDispatcher) {
            try {
                OperationResult.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                OperationResult.Failure(OperationError.UnknownError(reason = reason))
            }
        }
}

/**
 * A learned plan ↔ its `learned_plans` row. The template is JSON — `{"steps":[{"tool":…,"args":{name: arg}}]}`, an
 * argument `{"type":"slot","index":n}`, `{"type":"constant","text":…}` or `{"type":"ref","step":n,"key":…}` — and
 * holds no package: an app argument is a slot, bound again on every replay (spec §8.2). This format is persisted;
 * a change to it makes the old rows unreadable, and those are deleted when met (B-9) — the user teaches them again.
 */
internal object LearnedPlanRows {

    private val json = Json

    fun toEntity(plan: LearnedPlan): LearnedPlanEntity = LearnedPlanEntity(
        form = plan.shape.form.key,
        templateJson = json.encodeToString(TemplateDto.serializer(), TemplateDto.of(plan.shape.template)),
        streak = plan.streak,
        confirmations = plan.confirmations,
        confirmedAtEpochMs = plan.confirmedAtEpochMs,
    )

    /** The row as a plan, or `null` when its template cannot be read (see [LearnedPlanStoreImpl]). */
    fun toDomain(row: LearnedPlanEntity): LearnedPlan? {
        val template = try {
            json.decodeFromString(TemplateDto.serializer(), row.templateJson).toTemplate()
        } catch (e: IllegalArgumentException) {
            return null
        }
        return LearnedPlan(
            shape = LearnedPlanShape(CommandForm.parse(row.form), template),
            streak = row.streak,
            confirmations = row.confirmations,
            confirmedAtEpochMs = row.confirmedAtEpochMs,
        )
    }

    @Serializable
    private data class TemplateDto(val steps: List<StepDto>) {
        fun toTemplate(): PlanTemplate {
            require(steps.isNotEmpty()) { "a learned template has at least one step" }
            return PlanTemplate(steps.map { step -> TemplateStep(ToolId(step.tool), step.args.mapValues { (_, arg) -> arg.toArg() }) })
        }

        companion object {
            fun of(template: PlanTemplate) = TemplateDto(
                template.steps.map { step -> StepDto(step.tool.value, step.args.mapValues { (_, arg) -> ArgDto.of(arg) }) },
            )
        }
    }

    @Serializable
    private data class StepDto(val tool: String, val args: Map<String, ArgDto>)

    @Serializable
    private sealed interface ArgDto {
        fun toArg(): TemplateArg

        @Serializable
        @SerialName("slot")
        data class Slot(val index: Int) : ArgDto {
            override fun toArg() = TemplateArg.Slot(index)
        }

        @Serializable
        @SerialName("constant")
        data class Constant(val text: String) : ArgDto {
            override fun toArg() = TemplateArg.Constant(text)
        }

        @Serializable
        @SerialName("ref")
        data class Ref(val step: Int, val key: String) : ArgDto {
            override fun toArg() = TemplateArg.Ref(step, key)
        }

        companion object {
            fun of(arg: TemplateArg): ArgDto = when (arg) {
                is TemplateArg.Slot -> Slot(arg.index)
                is TemplateArg.Constant -> Constant(arg.text)
                is TemplateArg.Ref -> Ref(arg.step, arg.key)
            }
        }
    }
}
