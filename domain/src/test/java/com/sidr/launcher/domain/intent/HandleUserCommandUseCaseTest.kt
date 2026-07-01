package com.sidr.launcher.domain.intent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.domain.history.IntentMatchHistoryRepository
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.history.IntentMatchType
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandleUserCommandUseCaseTest {

    private val fakeRepo = FakeInstalledAppsRepository()
    private val fakeMatcher = FakeIntentMatcher()
    private val fakeExecutor = FakeActionExecutor()
    private val resolver = IntentActionResolver(fakeRepo)
    private val policy = DefaultIntentConfidencePolicy() // autoExecute = 0.85, suggest = 0.50

    // Fire-and-forget recording (HandleUserCommandUseCase.handle → recordingScope.launch) is driven
    // deterministically: the scope shares this StandardTestDispatcher's scheduler with runTest, so a
    // queued recordMatch runs only when the test calls advanceUntilIdle() — replacing the former
    // Dispatchers.Unconfined "runs in-place" reliance (Block H, step H-c).
    private val testDispatcher = StandardTestDispatcher()
    private val recordingScope = CoroutineScope(testDispatcher + SupervisorJob())

    private val useCase = HandleUserCommandUseCase(
        matcher = fakeMatcher,
        resolver = resolver,
        executor = fakeExecutor,
        confidencePolicy = policy,
        recordingScope = recordingScope,
    )

    @Before fun setUp() {
        fakeRepo.reset()
        fakeMatcher.reset()
        fakeExecutor.reset()
    }

    // ── High confidence: execute ─────────────────────────────────────────────

    @Test fun `high-confidence launch with resolved app executes`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        val outcome = useCase.handle("open telegram")

        assertEquals(CommandOutcome.Executed, outcome)
        val action = fakeExecutor.executedActions.single()
        assertTrue(action is ExecutableAction.LaunchAppAction)
        assertEquals("org.telegram.messenger", (action as ExecutableAction.LaunchAppAction).packageName)
    }

    @Test fun `high-confidence search executes via executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SearchIntent("weather", SearchTarget.WEB), 0.90f)

        val outcome = useCase.handle("search weather")

        assertEquals(CommandOutcome.Executed, outcome)
        assertTrue(fakeExecutor.executedActions.single() is ExecutableAction.OpenSearchAction)
    }

    @Test fun `confidence exactly at auto-execute threshold executes`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.85f)

        assertEquals(CommandOutcome.Executed, useCase.handle("open telegram"))
        assertEquals(1, fakeExecutor.callCount)
    }

    // ── Execution failure → Failed (safe message) ────────────────────────────

    @Test fun `executor failure maps to Failed with its safe message`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure("Couldn't open Telegram")

        val outcome = useCase.handle("open telegram")

        assertTrue(outcome is CommandOutcome.Failed)
        assertEquals("Couldn't open Telegram", (outcome as CommandOutcome.Failed).message)
    }

    // ── Confidence gate: medium / low ────────────────────────────────────────

    @Test fun `medium confidence suggests and does not execute`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.LaunchAppIntent("teleg"), 0.60f)

        val outcome = useCase.handle("teleg")

        assertTrue(outcome is CommandOutcome.Suggest)
        assertEquals(0.60f, (outcome as CommandOutcome.Suggest).confidence)
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `confidence exactly at suggest threshold suggests, not low`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.LaunchAppIntent("teleg"), 0.50f)

        assertTrue(useCase.handle("teleg") is CommandOutcome.Suggest)
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `low confidence non-unknown asks to clarify and does not execute`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.LaunchAppIntent("t"), 0.30f)

        assertEquals(CommandOutcome.LowConfidence, useCase.handle("t"))
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `low confidence unknown intent returns Unknown with original input`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.UnknownIntent(originalInput = "zzz", reason = "no rule"), 0.10f)

        val outcome = useCase.handle("zzz")

        assertTrue(outcome is CommandOutcome.Unknown)
        assertEquals("zzz", (outcome as CommandOutcome.Unknown).input)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ── Empty input ──────────────────────────────────────────────────────────

    @Test fun `blank input returns Empty without matching or executing`() = runTest(testDispatcher) {
        val outcome = useCase.handle("   ")

        assertEquals(CommandOutcome.Empty, outcome)
        assertEquals(0, fakeMatcher.callCount)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ── Resolver outcomes: ambiguous / not-found / repo failure ──────────────

    @Test fun `ambiguous app match returns NeedsConfirmation without executing`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Maps"),
            InstalledApp("com.b", "Maps"),
        )
        matcherReturns(LauncherIntent.LaunchAppIntent("maps"), 0.90f)

        val outcome = useCase.handle("open maps")

        assertTrue(outcome is CommandOutcome.NeedsConfirmation)
        assertEquals(2, (outcome as CommandOutcome.NeedsConfirmation).candidates.size)
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `app not found returns Message without executing`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("whatsapp"), 0.90f)

        assertTrue(useCase.handle("open whatsapp") is CommandOutcome.Message)
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `repository technical failure maps to Failed with safe message`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.UnknownError("db crash")
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        val outcome = useCase.handle("open telegram")

        assertTrue(outcome is CommandOutcome.Failed)
        // Safe message must not leak internal error detail.
        assertFalse((outcome as CommandOutcome.Failed).message.contains("db crash"))
        assertEquals(0, fakeExecutor.callCount)
    }

    // ── Simple commands: routed before resolver, never executed ──────────────

    @Test fun `OPEN_ASSISTANT routes to OpenAssistant, not executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SimpleCommandIntent(SimpleCommand.OPEN_ASSISTANT), 0.95f)

        assertEquals(CommandOutcome.OpenAssistant, useCase.handle("assistant"))
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `CLEAR routes to ClearInput, not executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SimpleCommandIntent(SimpleCommand.CLEAR), 0.95f)

        assertEquals(CommandOutcome.ClearInput, useCase.handle("clear"))
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `SHOW_APPS routes to ShowApps, not executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SimpleCommandIntent(SimpleCommand.SHOW_APPS), 0.95f)

        assertEquals(CommandOutcome.ShowApps, useCase.handle("show apps"))
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `HELP routes to a Message`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SimpleCommandIntent(SimpleCommand.HELP), 0.95f)

        assertTrue(useCase.handle("help") is CommandOutcome.Message)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ── Settings route & NoOp ────────────────────────────────────────────────

    @Test fun `open settings routes to OpenSettings, never the executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.OpenSettingsIntent(), 0.95f)

        assertEquals(CommandOutcome.OpenSettings, useCase.handle("settings"))
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `NoOpAction maps to NoOp without clearing or executing`() = runTest(testDispatcher) {
        // Resolver returns NoOpAction for UnknownIntent; force it past the gate at high confidence.
        matcherReturns(LauncherIntent.UnknownIntent(originalInput = "weird", reason = "forced"), 0.95f)

        assertEquals(CommandOutcome.NoOp, useCase.handle("weird"))
        assertEquals(0, fakeExecutor.callCount)
    }

    // ── Intent-match history recording (Block F, F5) — best-effort side effect ─

    // Recording is fire-and-forget on the shared testDispatcher scheduler: handle() returns before
    // the record is written, so the recording assertions run advanceUntilIdle() first to flush the
    // launched coroutine deterministically (replaces the old Dispatchers.Unconfined in-place hack).

    @Test fun `match is recorded with normalized text, type and confidence`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val useCaseWithHistory = useCaseWith(history, fixedNow = 1234L)
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        val outcome = useCaseWithHistory.handle("open telegram")
        advanceUntilIdle() // flush the fire-and-forget recordMatch

        assertEquals(CommandOutcome.Executed, outcome) // outcome unaffected by recording
        val record = history.recorded.single()
        assertEquals("open telegram", record.normalizedText)
        assertEquals(IntentMatchType.LAUNCH_APP, record.matchType)
        assertEquals(0.90, record.confidence, 0.0001)
        assertEquals(1234L, record.timestampEpochMs)
    }

    @Test fun `suggest and low-confidence matches are still recorded`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val useCaseWithHistory = useCaseWith(history)
        matcherReturns(LauncherIntent.LaunchAppIntent("teleg"), 0.60f) // medium → Suggest

        assertTrue(useCaseWithHistory.handle("teleg") is CommandOutcome.Suggest)
        advanceUntilIdle() // flush the fire-and-forget recordMatch
        assertEquals(1, history.recorded.size)
        assertEquals(0.60, history.recorded.single().confidence, 0.0001)
    }

    @Test fun `history write Failure does not break the command outcome`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory(
            result = OperationResult.Failure(OperationError.UnknownError("db down"))
        )
        val useCaseWithHistory = useCaseWith(history)
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        // Still executes; the failed write is swallowed, never becomes Failed.
        assertEquals(CommandOutcome.Executed, useCaseWithHistory.handle("open telegram"))
        advanceUntilIdle() // flush the fire-and-forget recordMatch
        assertEquals(1, history.recorded.size)
    }

    @Test fun `history write that throws does not break the command outcome`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory(throwOnRecord = true)
        val useCaseWithHistory = useCaseWith(history)
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        // Exception is swallowed inside recordMatch's catch; outcome is unaffected.
        assertEquals(CommandOutcome.Executed, useCaseWithHistory.handle("open telegram"))
        advanceUntilIdle() // flush the fire-and-forget recordMatch (its thrown error stays contained)
    }

    @Test fun `null history repository is a no-op and does not affect outcome`() = runTest(testDispatcher) {
        // The default useCase has no history repo; recording must be skipped silently.
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        assertEquals(CommandOutcome.Executed, useCase.handle("open telegram"))
    }

    // ── Feature-flag gate (Block F remediation) ──────────────────────────────

    @Test fun `flag disabled — match is NOT recorded even when history repo is wired`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = false))
        val uc = useCaseWith(history, featureFlagRepository = flagRepo)
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        val outcome = uc.handle("open telegram")
        advanceUntilIdle() // flush recordMatch so a (non-)record is observable

        assertEquals(CommandOutcome.Executed, outcome) // outcome identical — flag does not change routing
        assertTrue("Recording must be skipped when usageHistoryEnabled=false", history.recorded.isEmpty())
    }

    @Test fun `flag enabled — match IS recorded`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true))
        val uc = useCaseWith(history, featureFlagRepository = flagRepo)
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        val outcome = uc.handle("open telegram")
        advanceUntilIdle() // flush the fire-and-forget recordMatch

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(1, history.recorded.size)
    }

    @Test fun `flag-read throws non-cancellation — outcome unaffected, record skipped`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val throwingFlagRepo = object : FeatureFlagRepository {
            override fun getFlags(): Flow<FeatureFlags> =
                flow { throw RuntimeException("flag read error") }
            override suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit> =
                OperationResult.Success(Unit)
        }
        val uc = useCaseWith(history, featureFlagRepository = throwingFlagRepo)
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        matcherReturns(LauncherIntent.LaunchAppIntent("telegram"), 0.90f)

        val outcome = uc.handle("open telegram")
        advanceUntilIdle() // flush recordMatch; the thrown flag-read error stays contained

        assertEquals(CommandOutcome.Executed, outcome)
        assertTrue("Record must be skipped when getFlags() throws", history.recorded.isEmpty())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun matcherReturns(intent: LauncherIntent, confidence: Float) {
        fakeMatcher.intentToReturn = intent
        fakeMatcher.confidenceToReturn = confidence
    }

    private fun useCaseWith(
        history: IntentMatchHistoryRepository,
        fixedNow: Long = 0L,
        featureFlagRepository: FeatureFlagRepository? = null,
        recordingScope: CoroutineScope = this.recordingScope,
    ) = HandleUserCommandUseCase(
        matcher = fakeMatcher,
        resolver = resolver,
        executor = fakeExecutor,
        confidencePolicy = policy,
        intentMatchHistory = history,
        now = { fixedNow },
        featureFlagRepository = featureFlagRepository,
        recordingScope = recordingScope,
    )

    /** Local test double — the shared :core:testing fake lands in F8. */
    private class RecordingIntentMatchHistory(
        private val result: OperationResult<Unit> = OperationResult.Success(Unit),
        private val throwOnRecord: Boolean = false,
    ) : IntentMatchHistoryRepository {
        val recorded = mutableListOf<IntentMatchRecord>()

        override fun getMatchRecords(): Flow<List<IntentMatchRecord>> = flowOf(recorded.toList())

        override suspend fun recordMatch(record: IntentMatchRecord): OperationResult<Unit> {
            recorded += record
            if (throwOnRecord) throw RuntimeException("boom")
            return result
        }
    }
}
