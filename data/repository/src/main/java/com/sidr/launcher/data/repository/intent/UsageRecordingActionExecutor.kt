package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Records every app launch in ONE place (A4′ tail fix round, task 2, phase 5). The grid, the drawer and a launch by
 * command (through the agent engine, step `launch_app`) all end in [ActionExecutor.execute]; recording there means
 * `app_usage` — and so Home's favourites — sees them all, where it used to see only the two surfaces that kept their
 * own copy of the write.
 *
 * After [delegate] returns: a [ExecutableAction.LaunchAppAction] that came back [ActionExecutionResult.Success], with
 * `usageHistoryEnabled` on, writes one row stamped by [nowMs]. A failed write, a failed flag read and any other
 * throwable except [CancellationException] are swallowed — the launch already happened and its result is returned
 * exactly as the delegate gave it. Wraps `AndroidActionExecutor` in the graph (`IntentBindsModule`); it adds no call
 * site of `ToolExecutor` or `ToolWorker`.
 */
class UsageRecordingActionExecutor(
    private val delegate: ActionExecutor,
    private val usage: UsageHistoryRepository,
    private val flags: FeatureFlagRepository,
    private val nowMs: () -> Long,
) : ActionExecutor {

    override suspend fun execute(action: ExecutableAction): ActionExecutionResult {
        val result = delegate.execute(action)
        if (action is ExecutableAction.LaunchAppAction && result is ActionExecutionResult.Success) {
            record(action.packageName)
        }
        return result
    }

    private suspend fun record(packageName: String) {
        try {
            if (!flags.getFlags().first().usageHistoryEnabled) return
            usage.recordLaunch(packageName, nowMs())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Non-critical: the launch already completed successfully.
        }
    }
}
