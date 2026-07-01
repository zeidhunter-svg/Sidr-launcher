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
 * Pipeline: normalize → (empty?) → match → confidence gate → route → execute-when-safe.
 *
 * Invariants:
 * - Never throws to the UI. Technical failures from the resolver/executor become
 *   [CommandOutcome.Failed] with a safe message — they do NOT bubble up as exceptions or
 *   [OperationResult.Failure].
 * - Not everything is executed: navigation ([CommandOutcome.OpenAssistant] /
 *   [CommandOutcome.OpenSettings]), input clearing ([CommandOutcome.ClearInput]), grid display
 *   ([CommandOutcome.ShowApps]), ambiguity and "not found" never reach the [ActionExecutor].
 * - Low/medium confidence never auto-executes.
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
        val confidence = match.best.confidence

        // Fire-and-forget: record on the injected scope so the I/O never delays the outcome.
        // Placed BEFORE the confidence gate so suggest, low-confidence and unknown matches are
        // all captured. Never affects the outcome below.
        recordingScope.launch { recordMatch(normalized, intent, confidence) }

        // ── Confidence gate — runs before any resolution or execution ────────
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

        return when (val resolved = resolver.resolve(intent)) {
            is OperationResult.Failure -> CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)
            is OperationResult.Success -> routeAction(resolved.value)
        }
    }

    private fun routeSimpleCommand(command: SimpleCommand): CommandOutcome = when (command) {
        SimpleCommand.OPEN_ASSISTANT -> CommandOutcome.OpenAssistant
        SimpleCommand.SHOW_APPS -> CommandOutcome.ShowApps
        SimpleCommand.CLEAR -> CommandOutcome.ClearInput
        SimpleCommand.HELP -> CommandOutcome.Message(HELP_MESSAGE)
    }

    private suspend fun routeAction(action: ExecutableAction): CommandOutcome = when (action) {
        // Side-effecting actions go through the executor.
        is ExecutableAction.LaunchAppAction,
        is ExecutableAction.OpenSearchAction,
        -> mapExecution(executor.execute(action))

        // Routing outcomes — never executed.
        is ExecutableAction.AmbiguousAppAction -> CommandOutcome.NeedsConfirmation(action.candidates)
        is ExecutableAction.ShowMessageAction -> CommandOutcome.Message(action.message)
        ExecutableAction.OpenLauncherSettingsAction -> CommandOutcome.OpenSettings
        ExecutableAction.NoOpAction -> CommandOutcome.NoOp
    }

    private fun mapExecution(result: ActionExecutionResult): CommandOutcome = when (result) {
        is ActionExecutionResult.Success -> CommandOutcome.Executed
        is ActionExecutionResult.Failure -> CommandOutcome.Failed(result.safeMessage)
        is ActionExecutionResult.Unsupported -> CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)
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

    private fun LauncherIntent.toMatchType(): IntentMatchType = when (this) {
        is LauncherIntent.LaunchAppIntent     -> IntentMatchType.LAUNCH_APP
        is LauncherIntent.SearchIntent        -> IntentMatchType.SEARCH
        is LauncherIntent.OpenSettingsIntent  -> IntentMatchType.OPEN_SETTINGS
        is LauncherIntent.SimpleCommandIntent -> IntentMatchType.SIMPLE_COMMAND
        is LauncherIntent.UnknownIntent       -> IntentMatchType.UNKNOWN
    }

    private companion object {
        const val SAFE_FAILURE_MESSAGE = "Something went wrong. Please try again."
        const val HELP_MESSAGE = "Try: open <app>, search <query>, show apps, clear"
    }
}
