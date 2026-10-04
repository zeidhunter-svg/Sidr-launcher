package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.history.IntentMatchHistoryRepository
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.history.IntentMatchType
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Use case: turns raw user command text into a single [CommandOutcome] for the UI.
 *
 * Pipeline: normalize → (empty?) → match → resolve a claimed launch → confidence gate → route →
 * execute-when-safe.
 *
 * Invariants:
 * - Never throws to the UI. Technical failures from the resolver/executor become
 *   [CommandOutcome.Failed] with a safe message — they do NOT bubble up as exceptions or
 *   [OperationResult.Failure].
 * - Not everything is executed: navigation ([CommandOutcome.OpenAssistant] /
 *   [CommandOutcome.OpenSettings]), input clearing ([CommandOutcome.ClearInput]), grid display
 *   ([CommandOutcome.ShowApps]), ambiguity and "not found" never reach the [ActionExecutor].
 * - Low/medium confidence never auto-executes.
 * - A claimed launch's confidence is EARNED, not assumed (A4′ phase 1, spec §5; owner forks F1-2,
 *   F1-3). A launch whose app name does not resolve keeps its claim when that is a fact about the
 *   world — "открой wattsupp", or an installed brand's variant that is not installed ("открой youtube
 *   music"): `NoAppFound`, and `RouteCommandUseCase` step (2) offers the store, byte for byte as A0
 *   shipped it. When the name carries a second goal ([SecondGoal]: "открой gmail и проверь почту"),
 *   the parse folded two goals into one name and the claim is withdrawn:
 *   [CommandOutcome.LowConfidence], undecided, so step 2b and the model get the command.
 */
class HandleUserCommandUseCase(
    private val matcher: IntentMatcher,
    private val resolver: IntentActionResolver,
    private val executor: ActionExecutor,
    private val confidencePolicy: IntentConfidencePolicy,
    // Optional best-effort sink for intent-match history (Block F). Default-null keeps every
    // existing construction/test valid and makes recording strictly opt-in.
    private val intentMatchHistory: IntentMatchHistoryRepository? = null,
    // Injectable clock for deterministic tests; defaults to wall-clock.
    private val now: () -> Long = { System.currentTimeMillis() },
    // Flag gate — when provided, recording is skipped unless usageHistoryEnabled is true.
    // Default-null preserves backward compatibility: existing callers without the flag still record.
    private val featureFlagRepository: FeatureFlagRepository? = null,
    // Required: scope on which history records are launched fire-and-forget. In production DI
    // provides @ApplicationScope (SupervisorJob + IoDispatcher, singleton). Tests inject an
    // Unconfined scope. No default — an unscoped CoroutineScope(SupervisorJob()) would land on
    // Dispatchers.Default with no lifecycle, leaking work on every command.
    private val recordingScope: CoroutineScope,
) {

    suspend fun handle(rawInput: String): CommandOutcome {
        val normalized = CommandNormalizer.normalize(rawInput)
        if (normalized.isEmpty()) return CommandOutcome.Empty

        val match = matcher.match(normalized)
        val intent = match.best.intent

        // A4′ phase 1 (spec §5; owner forks F1-2, F1-3): a launch the matcher claimed is RESOLVED
        // before its confidence is believed. "открой gmail и проверь новые письма" is claimed at 0.90
        // exactly like "открой gmail"; whether that 0.90 was earned depends on what the name resolved
        // to. This is a read — nothing executes before the gate below — and its result is reused after
        // the gate, so a launch still reads the installed apps exactly once.
        val launchResolution = (intent as? LauncherIntent.LaunchAppIntent)
            ?.takeIf { confidencePolicy.shouldAutoExecute(match.best.confidence) }
            ?.let { resolver.resolve(it) }
        val confidence =
            if (launchResolution != null && overreaches(intent as LauncherIntent.LaunchAppIntent, launchResolution)) {
                UNEARNED_LAUNCH_CONFIDENCE
            } else {
                match.best.confidence
            }

        // Fire-and-forget: record on the injected scope so the I/O never delays the outcome.
        // Placed BEFORE the confidence gate so suggest, low-confidence and unknown matches are
        // all captured. Never affects the outcome below. Since A4′ phase 1 the confidence recorded
        // is the one the gate acts on, not the one the matcher claimed.
        recordingScope.launch { recordMatch(normalized, intent, confidence) }

        // ── Confidence gate — runs before any execution ──────────────────────
        if (confidencePolicy.isLowConfidence(confidence)) {
            return when (intent) {
                is LauncherIntent.UnknownIntent -> CommandOutcome.Unknown(intent.originalInput)
                else -> CommandOutcome.LowConfidence
            }
        }
        if (!confidencePolicy.shouldAutoExecute(confidence)) {
            return CommandOutcome.Suggest(intent, confidence)
        }

        // ── High confidence — route by intent ────────────────────────────────
        // SimpleCommandIntent bypasses the resolver: the resolver collapses the command
        // identity into a generic action, and these routes (navigate / clear / show apps)
        // are UI/navigation concerns, not execution.
        if (intent is LauncherIntent.SimpleCommandIntent) {
            return routeSimpleCommand(intent.command)
        }

        return when (val resolved = launchResolution ?: resolver.resolve(intent)) {
            is OperationResult.Failure -> CommandOutcome.Failed(CommandFailure.Generic)
            is OperationResult.Success -> routeAction(resolved.value)
        }
    }

    /**
     * A claimed launch whose app name did not resolve: is that a fact about the WORLD (no such app —
     * keep `NoAppFound`, and A0's store plan) or about the PARSE (FastPath folded a second goal into
     * the app name)? Only a second goal says "the parse" (owner fork F1-3, 2026-09-26). An installed
     * brand followed by more words — "открой youtube music", or "google maps" on a phone that labels
     * Maps «Карты» — is the world: the user named a variant that is not installed, and A0's store offer
     * is the right answer. Consulted only after the whole name failed to resolve, so an installed app
     * whose own label reads like a second goal ("Pack and Send") still launches (owner fork F1-2).
     */
    private fun overreaches(
        intent: LauncherIntent.LaunchAppIntent,
        resolution: OperationResult<ExecutableAction>,
    ): Boolean {
        val shown = (resolution as? OperationResult.Success)?.value as? ExecutableAction.ShowMessageAction
        if (shown?.message !is CommandMessage.NoAppFound) return false
        return SecondGoal.carriedBy(intent.displayNameQuery)
    }

    private fun routeSimpleCommand(command: SimpleCommand): CommandOutcome = when (command) {
        SimpleCommand.OPEN_ASSISTANT -> CommandOutcome.OpenAssistant(question = null)
        SimpleCommand.SHOW_APPS -> CommandOutcome.ShowApps
        SimpleCommand.CLEAR -> CommandOutcome.ClearInput
        SimpleCommand.HELP -> CommandOutcome.Message(CommandMessage.Help)
    }

    private suspend fun routeAction(action: ExecutableAction): CommandOutcome = when (action) {
        // Side-effecting actions go through the executor.
        is ExecutableAction.LaunchAppAction,
        is ExecutableAction.OpenSearchAction,
        is ExecutableAction.OpenUrlAction,
        is ExecutableAction.PlayStoreSearchAction,
        -> mapExecution(executor.execute(action))

        // Routing outcomes — never executed.
        is ExecutableAction.AmbiguousAppAction -> CommandOutcome.NeedsConfirmation(action.candidates)
        is ExecutableAction.ShowMessageAction -> CommandOutcome.Message(action.message)
        ExecutableAction.OpenLauncherSettingsAction -> CommandOutcome.OpenSettings
        ExecutableAction.NoOpAction -> CommandOutcome.NoOp
    }

    private fun mapExecution(result: ActionExecutionResult): CommandOutcome = when (result) {
        is ActionExecutionResult.Success -> CommandOutcome.Executed
        is ActionExecutionResult.Failure -> CommandOutcome.Failed(result.failure)
        is ActionExecutionResult.Unsupported -> CommandOutcome.Failed(CommandFailure.Generic)
    }

    /**
     * Records the match as a side effect. A null repository (default) is a no-op.
     * When [featureFlagRepository] is provided, recording is skipped unless
     * [com.sidr.launcher.domain.preferences.FeatureFlags.usageHistoryEnabled] is true.
     * The OperationResult is intentionally discarded and any throwable is swallowed — a failed or
     * unavailable history write must NEVER turn a command into [CommandOutcome.Failed] or throw.
     * Arbitrary-content match types (SEARCH, UNKNOWN) are redacted downstream in the data-layer
     * mapper (Fork 3); the use case passes the normalized text and the structural match type only.
     */
    private suspend fun recordMatch(normalized: String, intent: LauncherIntent, confidence: Float) {
        val repo = intentMatchHistory ?: return
        try {
            // Gate: if a flag repo is wired, skip the write when the flag is disabled.
            if (featureFlagRepository != null &&
                !featureFlagRepository.getFlags().first().usageHistoryEnabled
            ) return
            repo.recordMatch(
                IntentMatchRecord(
                    normalizedText = normalized,
                    matchType = intent.toMatchType(),
                    confidence = confidence.toDouble(),
                    timestampEpochMs = now(),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Swallow — history is non-critical; the command already has its outcome.
        }
    }

    private companion object {
        /**
         * What a launch verb whose app name cannot be used earns — the value `RuleBasedIntentMatcher`
         * already gives a launch verb with no app name. Below `DefaultIntentConfidencePolicy`'s suggest
         * threshold (0.50), so the gate answers `LowConfidence`: undecided, never `Suggest`. That holds
         * for the only policy production binds (`IntentProvidesModule.provideConfidencePolicy`); a
         * policy whose suggest threshold sat at or below this value would turn it into a suggestion to
         * launch a name that does not resolve — named here, not guarded.
         */
        const val UNEARNED_LAUNCH_CONFIDENCE = 0.30f
    }

    private fun LauncherIntent.toMatchType(): IntentMatchType = when (this) {
        is LauncherIntent.LaunchAppIntent     -> IntentMatchType.LAUNCH_APP
        is LauncherIntent.SearchIntent        -> IntentMatchType.SEARCH
        is LauncherIntent.OpenSettingsIntent  -> IntentMatchType.OPEN_SETTINGS
        is LauncherIntent.SimpleCommandIntent -> IntentMatchType.SIMPLE_COMMAND
        // OpenUrl/PlayStoreSearch carry arbitrary user content (a URL, an app-name query), so they are
        // classified as SEARCH — the data-layer mapper redacts SEARCH's normalizedText (Fork 3), keeping
        // the typed URL/query out of history. No new redaction surface is introduced.
        is LauncherIntent.OpenUrlIntent       -> IntentMatchType.SEARCH
        is LauncherIntent.PlayStoreSearchIntent -> IntentMatchType.SEARCH
        is LauncherIntent.UnknownIntent       -> IntentMatchType.UNKNOWN
    }

}
