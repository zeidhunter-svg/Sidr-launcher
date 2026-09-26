package com.sidr.launcher.data.repository.agent.memory

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 9, A1″ Phase 3a: `MemoryToolWorker`.
 *
 * **Deviations from the work-order's own sketch, found by reading the real signatures rather than
 * assumed (dispatch instruction: report every difference rather than working around it silently).**
 *  1. The work order's own sketch for `an over-length phrase is Failed…` passed `resolver =
 *     FakeResolver(...)` to a `memoryWorker(...)` helper. `MemoryToolWorker` takes **no** resolver
 *     (controller ruling R14-17 — revision 2 moved resolution into the planner, above the consent
 *     checkpoint, and the worker receives an already-resolved package); that parameter does not exist
 *     on this worker's constructor and the sketch does not compile against it. Removed.
 *  2. The sketch's `forget_app_alias deletes the phrase EXACTLY as the vocabulary normalized it` test
 *     invoked the worker with `"phrase" to "телега"` — a string that is **already** in
 *     `CommandNormalizer`-normalized form — and asserted the delete equals
 *     `CommandNormalizer.normalize("телега")`. Normalization is idempotent on an already-normalized
 *     string, so that assertion holds identically whether or not the worker (incorrectly) re-normalized
 *     the argument: it cannot fail on the property it names. Replaced below with a phrase that is
 *     **not** already normalized, so the two behaviours (pass-through vs. re-normalize) produce
 *     different results and the test can actually discriminate them.
 */
class MemoryToolWorkerTest {

    private class RecordingAliasStore : AliasStore {
        val upserted = mutableListOf<Alias>()
        val deleted = mutableListOf<String>()

        override suspend fun find(phrase: String): OperationResult<Alias?> =
            OperationResult.Success(upserted.firstOrNull { it.phrase == phrase })

        override suspend fun upsert(alias: Alias): OperationResult<Unit> {
            upserted += alias
            return OperationResult.Success(Unit)
        }

        override suspend fun delete(phrase: String): OperationResult<Unit> {
            deleted += phrase
            return OperationResult.Success(Unit)
        }

        override fun observeAll(): Flow<List<Alias>> = MutableStateFlow(upserted.toList())
    }

    private object FailingAliasStore : AliasStore {
        override suspend fun find(phrase: String): OperationResult<Alias?> =
            OperationResult.Failure(OperationError.UnknownError("io"))

        override suspend fun upsert(alias: Alias): OperationResult<Unit> =
            OperationResult.Failure(OperationError.UnknownError("io"))

        override suspend fun delete(phrase: String): OperationResult<Unit> =
            OperationResult.Failure(OperationError.UnknownError("io"))

        override fun observeAll(): Flow<List<Alias>> = MutableStateFlow(emptyList())
    }

    private class RecordingPreferenceStore : ResolutionPreferenceStore {
        val deleted = mutableListOf<Pair<CapabilityKey, ResolutionContext>>()

        override suspend fun find(
            key: CapabilityKey,
            context: ResolutionContext,
        ): OperationResult<ResolutionPreference?> = OperationResult.Success(null)

        override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> =
            OperationResult.Success(Unit)

        override suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> {
            deleted += key to context
            return OperationResult.Success(Unit)
        }

        override fun observeAll(): Flow<List<ResolutionPreference>> = MutableStateFlow(emptyList())
    }

    /**
     * `DeleteLearnedChoiceUseCase.delete` is `store.delete(key, context)` with no `try` of its own
     * (`DeleteLearnedChoiceUseCase.kt:5-7`) — the one use case of the three this worker cannot rely on
     * to have already mapped an exception to [OperationResult.Failure]. This store throws instead of
     * returning [OperationResult.Failure], to prove the worker's own catch is the net.
     */
    private object ThrowingPreferenceStore : ResolutionPreferenceStore {
        override suspend fun find(
            key: CapabilityKey,
            context: ResolutionContext,
        ): OperationResult<ResolutionPreference?> = OperationResult.Success(null)

        override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> =
            OperationResult.Success(Unit)

        override suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> =
            throw RuntimeException("resolution preference store blew up")

        override fun observeAll(): Flow<List<ResolutionPreference>> = MutableStateFlow(emptyList())
    }

    private fun memoryWorker(
        aliasStore: AliasStore = RecordingAliasStore(),
        preferenceStore: ResolutionPreferenceStore = RecordingPreferenceStore(),
    ): MemoryToolWorker = MemoryToolWorker(
        save = SaveAliasUseCase(aliasStore),
        deleteAlias = DeleteAliasUseCase(aliasStore),
        deleteChoice = DeleteLearnedChoiceUseCase(preferenceStore),
    )

    @Test
    fun `set_app_alias stores the alias against the package the planner resolved`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(aliasStore = store)

        // R14-17: `app` arrives ALREADY RESOLVED (Task 6b's planner-side resolution), and this worker
        // never calls a resolver of its own — it has none to call.
        val result = worker.invoke(
            ResolvedInvocation(
                MemoryToolIds.SET_APP_ALIAS,
                mapOf("app" to "org.telegram.messenger", "app_label" to "телеграм", "phrase" to "телега"),
            ),
        )

        assertTrue(result is ToolResult.Effected)
        val stored = store.upserted.single()
        assertEquals("телега", stored.phrase)
        assertEquals(AliasTarget.App("org.telegram.messenger"), stored.target)
    }

    @Test
    fun `set_app_alias declines a blank package instead of storing an alias pointing nowhere`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(aliasStore = store)

        val result = worker.invoke(
            ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "", "phrase" to "x")),
        )

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, store.upserted.size)
    }

    /**
     * The property spec §7.6 names explicitly and requires held by a test, not assumed:
     * `DeleteAliasUseCase.delete` passes its argument to `AliasStore.delete` **unchanged** — it does not
     * normalize. `forget_app_alias` only ever matches the stored key because `ToolVocabulary` normalizes
     * on the way in (Task 10); this worker's own job is simply to not undo, or redundantly repeat, that
     * contract.
     *
     * The phrase below is deliberately **not** already normalized (mixed case, leading/trailing
     * whitespace), so the assertion can actually tell "passed through unchanged" apart from "silently
     * re-normalized" — the sanity assertion at the end confirms the input needed normalizing at all, so
     * this test could not have passed by accident the way the work order's own sketch could have.
     */
    @Test
    fun `forget_app_alias passes the phrase to the store UNCHANGED, never re-normalizing it`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(aliasStore = store)
        val raw = "  ТЕЛЕГА  "
        assertNotEquals(
            "sanity: this phrase must actually need normalizing, or the test below could pass whether " +
                "or not the worker re-normalizes",
            CommandNormalizer.normalize(raw),
            raw,
        )

        worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to raw)))

        assertEquals(
            "DeleteAliasUseCase does not normalize; forget_app_alias matches the stored key only " +
                "because ToolVocabulary normalized on the way in (Task 10). If this worker normalized " +
                "the phrase itself, the store would have received CommandNormalizer.normalize(raw) " +
                "instead of raw.",
            listOf(raw),
            store.deleted,
        )
    }

    @Test
    fun `forget_learned_choice deletes the launch_app key for that phrase, with no context`() = runTest {
        val store = RecordingPreferenceStore()
        val worker = memoryWorker(preferenceStore = store)

        worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_LEARNED_CHOICE, mapOf("phrase" to "банк")))

        assertEquals(
            listOf(CapabilityKey(ActionIds.LAUNCH_APP, "банк") to ResolutionContext.None),
            store.deleted,
        )
    }

    @Test
    fun `a store failure is Failed, never Effected`() = runTest {
        val worker = memoryWorker(aliasStore = FailingAliasStore)

        val result = worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "телега")))

        assertTrue(result is ToolResult.Failed)
    }

    /**
     * Review finding I6: `SaveAliasUseCase` answers `Success(Unit)` WITHOUT WRITING for a blank or
     * over-length phrase (`MAX_ALIAS_PHRASE_LENGTH` = 64). Passing that through as `Effected` would put
     * a success marker on the surface and `ToolObserved(Effected)` in the trace for an alias that does
     * not exist.
     */
    @Test
    fun `an over-length phrase is Failed, not a silent no-op reported as done`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(aliasStore = store)

        val result = worker.invoke(
            ResolvedInvocation(
                MemoryToolIds.SET_APP_ALIAS,
                mapOf("app" to "org.telegram.messenger", "phrase" to "x".repeat(65)),
            ),
        )

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, store.upserted.size)
    }

    @Test
    fun `a delete use case that throws is Failed, not an escaped exception`() = runTest {
        val worker = memoryWorker(preferenceStore = ThrowingPreferenceStore)

        val result = worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_LEARNED_CHOICE, mapOf("phrase" to "банк")))

        assertTrue(result is ToolResult.Failed)
    }

    /**
     * `AliasStore.delete` reports no row count, so the worker cannot distinguish "removed one" from
     * "there was none". `Effected` here means THE OPERATION RAN, not THAT SOMETHING WAS REMOVED — this
     * worker's own KDoc says exactly that sentence, and this test is what keeps the limitation a
     * documented property rather than a reader's inference.
     */
    @Test
    fun `forgetting a phrase that was never stored is still Effected`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(aliasStore = store)

        val result = worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "никогда")))

        assertTrue(result is ToolResult.Effected)
        assertEquals(listOf("никогда"), store.deleted)
    }

    @Test
    fun `an unrecognised id is Failed, unreachable in a well-formed graph`() = runTest {
        val worker = memoryWorker()

        val result = worker.invoke(
            ResolvedInvocation(com.sidr.launcher.domain.tool.ToolId("not_a_memory_tool")),
        )

        assertTrue(result is ToolResult.Failed)
    }
}
