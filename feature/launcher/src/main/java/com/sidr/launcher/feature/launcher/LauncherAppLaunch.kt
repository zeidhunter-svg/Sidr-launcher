package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Task 3 / A0. `launchApp()`/`recordUsage()`/`recordChoiceIfPending()` extracted unchanged from
 * `LauncherViewModel` — they are called from both the app grid ([LauncherAppList]'s tap path) and
 * the command pipeline (`onCommandSubmitted`'s AutoLaunch branch), so a shared collaborator both
 * sides hold is what keeps [LauncherAppList] and the future `LauncherCommandSession` (Task 4) from
 * depending on each other.
 *
 * [launch]'s signature is deliberately the original `packageName`/`activityName`/`onResult` shape,
 * not the illustrative `launch(app: InstalledApp)` from the task brief: the AutoLaunch branch in
 * `onCommandSubmitted` only ever has a package name plus an optionally-resolved activity name (the
 * app may still be loading), and `onSuggestionClicked`'s fallback branch launches a bare
 * `suggestion.actionId` with no [com.sidr.launcher.domain.model.InstalledApp] at all — copying the
 * ViewModel's actual current declaration per the controller's instruction.
 *
 * [CommandFeedback] is ViewModel-owned state this class has no access to, so [onFeedback] is the
 * narrow callback that replaces the inline `_commandFeedback.value = ...` write (same pattern as
 * [LauncherVoiceInput]'s callbacks).
 *
 * The scope split is load-bearing and preserved exactly: [launch] itself — and therefore the
 * feedback write and [recordUsage] it triggers on success — runs on [scope] (`viewModelScope`), so
 * it is cancelled if the ViewModel is cleared mid-flight, same as before. [recordChoiceIfPending]'s
 * actual store write runs on [applicationScope] on purpose (S2-1 Task 11): it must survive the user
 * navigating away right after a launch, which cancels `viewModelScope` but not the application scope.
 */
internal class LauncherAppLaunch(
    private val actionExecutor: ActionExecutor,
    private val usageHistoryRepository: UsageHistoryRepository,
    // Not part of the brief's illustrative constructor, but recordUsage() (moved verbatim) needs it
    // to gate the write on the usageHistoryEnabled flag exactly as before.
    private val featureFlagRepository: FeatureFlagRepository,
    private val recordResolutionChoice: RecordResolutionChoiceUseCase,
    private val applicationScope: CoroutineScope,
    private val scope: CoroutineScope,
    // Narrow callback for the one piece of ViewModel-owned state launchApp() used to touch directly.
    private val onFeedback: (CommandFeedback) -> Unit,
) {

    // S2-1 Task 11: transient, VM-internal only. Set whenever the last outcome was an app-ambiguity
    // list; consumed by a subsequent successful app launch to record the user's explicit choice.
    private val _pendingLearningToken = MutableStateFlow<ResolutionLearningToken?>(null)

    /** Arm/clear the pending learned-resolution token (see [_pendingLearningToken] above). */
    fun rememberLearningToken(token: ResolutionLearningToken?) {
        _pendingLearningToken.value = token
    }

    fun launch(
        packageName: String,
        activityName: String?,
        // S2-1 Task 11: optional success signal for callers that need to react to a real launch
        // result (e.g. recording a learned choice, or falling back on an AutoLaunch failure).
        // Existing callers pass nothing, so their behavior is byte-for-byte unchanged.
        onResult: ((success: Boolean) -> Unit)? = null,
    ) {
        scope.launch {
            val action = ExecutableAction.LaunchAppAction(
                packageName = packageName,
                activityName = activityName,
            )
            val result = actionExecutor.execute(action)
            onFeedback(
                when (result) {
                    is ActionExecutionResult.Success -> CommandFeedback.None
                    is ActionExecutionResult.Failure -> CommandFeedback.Failure(result.failure)
                    is ActionExecutionResult.Unsupported -> CommandFeedback.Failure(CommandFailure.Generic)
                },
            )
            // Record usage only on a successful launch — soft-wrapped, never blocks the launch.
            // Command-executed launches (CommandOutcome.Executed) are not tracked here because
            // the package name is not available at the ViewModel boundary; a future slice can
            // extend HandleUserCommandUseCase to carry it in the outcome.
            if (result is ActionExecutionResult.Success) {
                recordUsage(packageName)
            }
            onResult?.invoke(result is ActionExecutionResult.Success)
        }
    }

    private suspend fun recordUsage(packageName: String) {
        try {
            // Gate: skip the write when the user has not enabled usage-history tracking.
            if (!featureFlagRepository.getFlags().first().usageHistoryEnabled) return
            usageHistoryRepository.recordLaunch(packageName, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Non-critical — launch already completed successfully.
        }
    }

    /**
     * S2-1 Task 11: if the last outcome was an app-ambiguity list awaiting a choice, and
     * [packageName] is one of the candidates that list actually offered, records the explicit choice
     * fire-and-forget on [applicationScope] — the returned [com.sidr.launcher.domain.result.OperationResult]
     * is ignored (the launch already completed; a failed/aborted record must never surface as a launch
     * error). Any app launch (grid tap or candidate tap) ends the pending interaction. A grid tap with
     * no pending token, or a tap on an app the ambiguity list never offered, is a plain launch and
     * never records — the caller makes no other decision here; membership/streak/risk logic lives in
     * the use-cases.
     */
    fun recordChoiceIfPending(packageName: String) {
        val token = _pendingLearningToken.value ?: return
        _pendingLearningToken.value = null
        if (!token.isAppAmbiguityFlow) return
        val chosen = ResolvedTarget.App(packageName)
        if (chosen !in token.candidateSet.targets) return
        applicationScope.launch {
            try {
                recordResolutionChoice.record(token.capabilityKey, token.context, chosen, token.candidateSet)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Non-critical — the app already launched successfully.
            }
        }
    }
}
