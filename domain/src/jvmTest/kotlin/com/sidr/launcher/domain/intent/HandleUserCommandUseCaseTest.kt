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
        // A4′ phase 1 resolves a claimed launch before the gate and reuses that read after it: the
        // installed apps are read exactly once, not once per side of the gate.
        assertEquals(1, fakeRepo.callCount)
    }

    @Test fun `high-confidence search executes via executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SearchIntent("weather", SearchTarget.WEB), 0.90f)

        val outcome = useCase.handle("search weather")

        assertEquals(CommandOutcome.Executed, outcome)
        assertTrue(fakeExecutor.executedActions.single() is ExecutableAction.OpenSearchAction)
    }

    @Test fun `high-confidence open-url executes via executor (AIL-2)`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.OpenUrlIntent("https://github.com"), 0.90f)

        val outcome = useCase.handle("github.com")

        assertEquals(CommandOutcome.Executed, outcome)
        val action = fakeExecutor.executedActions.single()
        assertTrue(action is ExecutableAction.OpenUrlAction)
        assertEquals("https://github.com", (action as ExecutableAction.OpenUrlAction).url)
    }

    @Test fun `high-confidence play-store search executes via executor (AIL-2)`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.PlayStoreSearchIntent("whatsapp"), 0.90f)

        val outcome = useCase.handle("install whatsapp")

        assertEquals(CommandOutcome.Executed, outcome)
        assertTrue(fakeExecutor.executedActions.single() is ExecutableAction.PlayStoreSearchAction)
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
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure(CommandFailure.CantOpenApp)

        val outcome = useCase.handle("open telegram")

        assertTrue(outcome is CommandOutcome.Failed)
        assertEquals(CommandFailure.CantOpenApp, (outcome as CommandOutcome.Failed).failure)
    }

    // ── Confidence gate: medium / low ────────────────────────────────────────

    @Test fun `medium confidence suggests and does not execute`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.LaunchAppIntent("teleg"), 0.60f)

        val outcome = useCase.handle("teleg")

        assertTrue(outcome is CommandOutcome.Suggest)
        assertEquals(0.60f, (outcome as CommandOutcome.Suggest).confidence)
        assertEquals(0, fakeExecutor.callCount)
        // Below the auto-execute threshold a launch is never resolved: no installed-apps read at all.
        assertEquals(0, fakeRepo.callCount)
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
        // CommandFailure carries no message field at all - internal error detail cannot leak by construction.
        assertEquals(CommandFailure.Generic, (outcome as CommandOutcome.Failed).failure)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ── Simple commands: routed before resolver, never executed ──────────────

    @Test fun `OPEN_ASSISTANT routes to OpenAssistant, not executor`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SimpleCommandIntent(SimpleCommand.OPEN_ASSISTANT), 0.95f)

        assertEquals(CommandOutcome.OpenAssistant(question = null), useCase.handle("assistant"))
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

    // ── A4′ phase 1 (spec §5; owner forks F1-2, F1-3): a launch's confidence is earned ───────────
    // Fixtures (R14-43): the installed label "Lumora" (net.fixture.alpha), the uninstalled "corvane",
    // and the installed "Pack and Send" (net.fixture.parcel). "lumora studio" extends the installed
    // label ON PURPOSE — that is the case under test there. Every expected value is compared by
    // equality, never by `contains`, so a shared substring cannot blind an assertion.

    @Test fun `a launch whose app name carries a second goal does not claim confidence`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("corvane and send the report"), 0.90f)

        assertEquals(CommandOutcome.LowConfidence, useCase.handle("open corvane and send the report"))
        assertEquals(0, fakeExecutor.callCount)
    }

    /**
     * Owner fork F1-3 (2026-09-26): an installed brand followed by more words is a fact about the WORLD
     * — the user named a variant that is not installed ("открой youtube music", "google maps" on a
     * phone that labels Maps «Карты») — so `NoAppFound` is still claimed and step (2) of
     * `RouteCommandUseCase` still offers the store. Spec §5.1's prefix rule would withdraw this; it is
     * deferred until offline understanding exists (Master Plan row `B16`). Re-adding it must delete
     * this test on purpose.
     */
    @Test fun `a launch naming an installed brand's variant that is not installed is still claimed`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("lumora studio"), 0.90f)

        assertEquals(
            CommandOutcome.Message(CommandMessage.NoAppFound("lumora studio")),
            useCase.handle("открой lumora studio"),
        )
    }

    /**
     * The second goal is asked only AFTER the whole name failed to resolve (owner fork F1-2). An
     * installed app whose own label reads like a second goal must still launch.
     */
    @Test fun `an installed app whose own name carries a conjunction and a verb still launches`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.parcel", "Pack and Send"))
        matcherReturns(LauncherIntent.LaunchAppIntent("pack and send"), 0.90f)

        assertEquals(CommandOutcome.Executed, useCase.handle("open pack and send"))
        val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
        assertEquals("net.fixture.parcel", action.packageName)
    }

    /**
     * The history row must not claim a confidence that did not govern the outcome (DOC-ILM-3's
     * spirit). Stored as a Double, 0.30f reads back as 0.3000000119… — hence the tolerance, here and
     * in any query of the table.
     */
    @Test fun `an unearned launch is recorded with the confidence it earned, not the one it claimed`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val useCaseWithHistory = useCaseWith(history)
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("corvane and send the report"), 0.90f)

        useCaseWithHistory.handle("open corvane and send the report")
        advanceUntilIdle() // flush the fire-and-forget recordMatch

        assertEquals(0.30, history.recorded.single().confidence, 0.0001)
    }

    // ── A4′ acceptance tail, owner decision 13: Б — a confident search yields to a reachable model ─────
    // `yieldToModel` is the router's «the model is reachable» (not local-only, a provider, the network —
    // controller ruling K14). FastPath then does not run a confident search: it hands the search over
    // (`FastPathYield.Search`) and answers `LowConfidence`, so the router asks the model. Without the model it
    // searches exactly as before. Every query word is invented or ordinary; equality, never `contains`.

    @Test fun `a confident search yields to the model when asked to - nothing executes`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SearchIntent("лучший рецепт шашлыка", SearchTarget.WEB), 0.90f)

        val answer = useCase.answer("найди лучший рецепт шашлыка", yieldToModel = true)

        assertEquals(CommandOutcome.LowConfidence, answer.outcome)
        assertEquals(
            FastPathYield.Search(LauncherIntent.SearchIntent("лучший рецепт шашлыка", SearchTarget.WEB)),
            answer.yielded,
        )
        assertEquals("nothing is executed before the model is asked", 0, fakeExecutor.callCount)
    }

    @Test fun `a confident search is executed as before when the model is not reachable`() = runTest(testDispatcher) {
        matcherReturns(LauncherIntent.SearchIntent("лучший рецепт шашлыка", SearchTarget.WEB), 0.90f)

        val answer = useCase.answer("найди лучший рецепт шашлыка", yieldToModel = false)

        assertEquals(CommandOutcome.Executed, answer.outcome)
        assertEquals(
            ExecutableAction.OpenSearchAction("лучший рецепт шашлыка", SearchTarget.WEB),
            fakeExecutor.executedActions.single(),
        )
        assertEquals(null, answer.yielded)
    }

    /**
     * A plan decision: the yield is a ROUTE, not a confidence. The row says what the matcher claimed and the gate
     * acted on (0.90, `SEARCH`), so it stays comparable with the rows written before decision 13.
     */
    @Test fun `a yielded search records the matcher's confidence`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val useCaseWithHistory = useCaseWith(history)
        matcherReturns(LauncherIntent.SearchIntent("лучший рецепт шашлыка", SearchTarget.WEB), 0.90f)

        val answer = useCaseWithHistory.answer("найди лучший рецепт шашлыка", yieldToModel = true)
        advanceUntilIdle() // flush the fire-and-forget recordMatch

        assertEquals(CommandOutcome.LowConfidence, answer.outcome)
        val record = history.recorded.single()
        assertEquals(IntentMatchType.SEARCH, record.matchType)
        assertEquals(0.90, record.confidence, 0.0001)
    }

    /**
     * The router runs a yielded search only after `answer` has already recorded the command once; running it must
     * not write a second row. The history here is wired and enabled — a `runYielded` that recorded would succeed.
     */
    @Test fun `runYielded executes the search and records nothing`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val useCaseWithHistory = useCaseWith(history)

        val outcome = useCaseWithHistory.runYielded(
            FastPathYield.Search(LauncherIntent.SearchIntent("лучший рецепт шашлыка", SearchTarget.WEB)),
        )
        advanceUntilIdle() // flush anything a recording would have launched

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(
            ExecutableAction.OpenSearchAction("лучший рецепт шашлыка", SearchTarget.WEB),
            fakeExecutor.executedActions.single(),
        )
        assertTrue("runYielded must not record a second intent_match row", history.recorded.isEmpty())
    }

    /** Only a search yields: a launch, a settings route and a simple command answer exactly as without the model. */
    @Test fun `a launch, a setting and a simple command never yield`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.calc", "Калькулятор"))

        matcherReturns(LauncherIntent.LaunchAppIntent("калькулятор"), 0.90f)
        val launchYielding = useCase.answer("открой калькулятор", yieldToModel = true)
        val launchNot = useCase.answer("открой калькулятор", yieldToModel = false)

        matcherReturns(LauncherIntent.OpenSettingsIntent(), 0.95f)
        val settingsYielding = useCase.answer("настройки", yieldToModel = true)
        val settingsNot = useCase.answer("настройки", yieldToModel = false)

        matcherReturns(LauncherIntent.SimpleCommandIntent(SimpleCommand.HELP), 0.95f)
        val helpYielding = useCase.answer("помощь", yieldToModel = true)
        val helpNot = useCase.answer("помощь", yieldToModel = false)

        assertEquals(FastPathAnswer(CommandOutcome.Executed), launchYielding)
        assertEquals(launchNot, launchYielding)
        assertEquals(FastPathAnswer(CommandOutcome.OpenSettings), settingsYielding)
        assertEquals(settingsNot, settingsYielding)
        assertEquals(FastPathAnswer(CommandOutcome.Message(CommandMessage.Help)), helpYielding)
        assertEquals(helpNot, helpYielding)
        assertEquals(
            "the launch ran both times",
            listOf(
                ExecutableAction.LaunchAppAction("net.fixture.calc"),
                ExecutableAction.LaunchAppAction("net.fixture.calc"),
            ),
            fakeExecutor.executedActions,
        )
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
