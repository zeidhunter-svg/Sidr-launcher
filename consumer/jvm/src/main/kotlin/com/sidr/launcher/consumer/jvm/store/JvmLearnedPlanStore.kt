package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.LearnedPlanStore
import com.sidr.launcher.domain.memory.plan.MAX_LEARNED_PLANS
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/** A learned-plan file that cannot be decoded as a whole. Carries no text of the file. */
internal class CorruptLearnedPlanFileException(cause: Throwable) : Exception("the learned-plan file could not be decoded", cause)

/**
 * The second consumer's [LearnedPlanStore] (A4′ phase 4, plan §0.2 B-13): one JSON file,
 * `{"plans":[{"form", "template", "streak", "confirmations", "confirmedAtEpochMs"}]}`, with the semantics of
 * the Room sibling (`LearnedPlanStoreImpl`) over a file.
 *
 * **It never throws** (rethrows [CancellationException] only): every I/O or decode error of a suspend
 * operation is an [OperationResult.Failure] with a fixed reason token — the exception's own text is never used,
 * as in [JvmAgentSessionStore], because the decoder appends the offending input and the file holds the words
 * of the user's commands. [observeAll] is the exception, as on Android: a stream's failure is the flow's
 * exception.
 *
 * **A file that cannot be read as a whole is not an empty memory.** Its failure is reported and the file is
 * **left as it is**, so `RecordLearnedPlanUseCase` (which returns a failed `find`) never writes a fresh row
 * over what may be recoverable. A **row** whose template cannot be read (an unknown argument kind, a template
 * with no step) is, as on Android (B-9), deleted when met — it can never replay — and the file rewritten
 * without it; any other fault deletes nothing.
 *
 * **The cap** ([MAX_LEARNED_PLANS]) is applied in [upsert], as the Room store's `upsertCapped` does: unpinned
 * rows first (the lowest streak, then the oldest confirmation), a pinned row only when no unpinned one is
 * left (the oldest confirmation first), never the row just written.
 *
 * Writes go to a temp file moved over the live one (`ATOMIC_MOVE`), never truncating in place. **One process
 * at a time:** a path-keyed in-process [Mutex] excludes coroutines of this JVM; there is no `FileLock`, and no
 * `fsync` (named, as the session store names it). The file lives under the sandbox root like the session file
 * — reach, not a silent effect: a `remove` of it is a `DANGEROUS` plan the user must confirm.
 */
class JvmLearnedPlanStore(file: Path) : LearnedPlanStore {

    private val path: Path = file.toAbsolutePath().normalize()
    private val mutex: Mutex = gates.computeIfAbsent(path.toString()) { Mutex() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    override suspend fun find(form: CommandForm): OperationResult<LearnedPlan?> =
        guarded(READ_FAILED) { readAll().firstOrNull { it.shape.form.key == form.key } }

    override suspend fun all(): OperationResult<List<LearnedPlan>> = guarded(READ_FAILED) { readAll() }

    override suspend fun upsert(plan: LearnedPlan): OperationResult<Unit> = guarded(WRITE_FAILED) {
        val others = readAll().filter { it.shape.form.key != plan.shape.form.key }
        write(capped(others + plan, plan))
    }

    override suspend fun delete(form: CommandForm): OperationResult<Unit> = guarded(DELETE_FAILED) {
        val rows = readAll()
        val kept = rows.filter { it.shape.form.key != form.key }
        if (kept.size != rows.size) write(kept)
    }

    override fun observeAll(): Flow<List<LearnedPlan>> = flow {
        when (val read = all()) {
            is OperationResult.Success -> emit(read.value)
            is OperationResult.Failure -> throw IOException("the learned plans could not be read")
        }
    }

    /** The rows of the file; an unreadable template's row is deleted (the file rewritten without it) and left out. */
    private fun readAll(): List<LearnedPlan> {
        if (!Files.exists(path)) return emptyList()
        val text = Files.readString(path)
        if (text.isBlank()) return emptyList()
        val dto = try {
            json.decodeFromString(FileDto.serializer(), text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw CorruptLearnedPlanFileException(e)
        }
        val plans = dto.plans.mapNotNull { row -> readRow(row) }
        if (plans.size != dto.plans.size) write(plans)
        return plans
    }

    private fun readRow(row: RowDto): LearnedPlan? {
        val template = try {
            json.decodeFromJsonElement(TemplateDto.serializer(), row.template).toTemplate()
        } catch (e: IllegalArgumentException) { // the decoder's documented refusal; SerializationException is one
            return null
        }
        return LearnedPlan(
            shape = LearnedPlanShape(CommandForm.parse(row.form), template),
            streak = row.streak,
            confirmations = row.confirmations,
            confirmedAtEpochMs = row.confirmedAtEpochMs,
        )
    }

    /** [rows] cut to [MAX_LEARNED_PLANS], [written] never the one cut. */
    private fun capped(rows: List<LearnedPlan>, written: LearnedPlan): List<LearnedPlan> {
        val kept = rows.toMutableList()
        while (kept.size > MAX_LEARNED_PLANS) {
            val others = kept.filter { it !== written }
            val unpinned = others.filter { !it.pinned }
            val victim = if (unpinned.isNotEmpty()) {
                unpinned.minWith(compareBy<LearnedPlan>({ it.streak }, { it.confirmedAtEpochMs }))
            } else {
                others.minBy { it.confirmedAtEpochMs }
            }
            kept.remove(victim)
        }
        return kept
    }

    private fun write(plans: List<LearnedPlan>) {
        val parent = path.parent
        Files.createDirectories(parent)
        sweepStaleTemps()
        val temp = Files.createTempFile(parent, path.fileName.toString(), TEMP_SUFFIX)
        try {
            val dto = FileDto(plans.map { plan ->
                RowDto(
                    form = plan.shape.form.key,
                    template = json.encodeToJsonElement(TemplateDto.serializer(), TemplateDto.of(plan.shape.template)),
                    streak = plan.streak,
                    confirmations = plan.confirmations,
                    confirmedAtEpochMs = plan.confirmedAtEpochMs,
                )
            })
            Files.writeString(temp, json.encodeToString(FileDto.serializer(), dto), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Throwable) {
            Files.deleteIfExists(temp)
            throw e
        }
    }

    /** Temp files orphaned by a process that died inside [write]; matched by our own prefix and suffix, plain strings. */
    private fun sweepStaleTemps() {
        val parent = path.parent ?: return
        if (!Files.isDirectory(parent)) return
        val live = path.fileName?.toString() ?: return
        Files.newDirectoryStream(parent).use { entries ->
            entries.forEach { candidate ->
                val name = candidate.fileName?.toString() ?: return@forEach
                if (name != live && name.startsWith(live) && name.endsWith(TEMP_SUFFIX)) {
                    try {
                        Files.deleteIfExists(candidate)
                    } catch (_: IOException) {
                        // Best-effort: a temp that cannot be deleted must not fail the write asked for.
                    }
                }
            }
        }
    }

    private suspend fun <T> guarded(reason: String, block: () -> T): OperationResult<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    OperationResult.Success(block())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: CorruptLearnedPlanFileException) {
                    OperationResult.Failure(OperationError.UnknownError(reason = CORRUPT))
                } catch (_: Throwable) {
                    OperationResult.Failure(OperationError.UnknownError(reason = reason))
                }
            }
        }

    @Serializable
    private data class FileDto(val plans: List<RowDto> = emptyList())

    @Serializable
    private data class RowDto(
        val form: String,
        val template: JsonElement,
        val streak: Int,
        val confirmations: Int,
        val confirmedAtEpochMs: Long,
    )

    /** The template as the Room sibling persists it: `{"steps":[{"tool":…,"args":{name: arg}}]}`; no package, ever (spec §8.2). */
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

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
        const val READ_FAILED = "file_learned_plan_read_failed"
        const val WRITE_FAILED = "file_learned_plan_write_failed"
        const val DELETE_FAILED = "file_learned_plan_delete_failed"
        const val CORRUPT = "file_learned_plan_corrupt"

        /** One [Mutex] per file in this JVM (lexical path key), as [JvmAgentSessionStore]'s gate. */
        private val gates = ConcurrentHashMap<String, Mutex>()
    }
}
