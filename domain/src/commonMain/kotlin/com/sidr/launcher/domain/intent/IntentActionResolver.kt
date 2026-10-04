package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import java.util.Locale

/**
 * Use case: maps a [LauncherIntent] to an [ExecutableAction].
 *
 * Normal resolution outcomes (not found, ambiguous) are returned as [OperationResult.Success]
 * with an appropriate action — they are not errors. [OperationResult.Failure] is reserved for
 * technical failures (e.g. repository I/O failure).
 */
class IntentActionResolver(
    private val repository: InstalledAppsRepository,
) {
    suspend fun resolve(intent: LauncherIntent): OperationResult<ExecutableAction> = when (intent) {
        is LauncherIntent.LaunchAppIntent -> resolveAppLaunch(intent)
        is LauncherIntent.SearchIntent -> OperationResult.Success(
            ExecutableAction.OpenSearchAction(query = intent.query, target = intent.target)
        )
        is LauncherIntent.OpenSettingsIntent -> OperationResult.Success(
            ExecutableAction.OpenLauncherSettingsAction
        )
        is LauncherIntent.SimpleCommandIntent -> OperationResult.Success(
            resolveSimpleCommand(intent.command)
        )
        is LauncherIntent.OpenUrlIntent -> OperationResult.Success(
            ExecutableAction.OpenUrlAction(url = intent.url)
        )
        is LauncherIntent.PlayStoreSearchIntent -> OperationResult.Success(
            ExecutableAction.PlayStoreSearchAction(query = intent.query)
        )
        is LauncherIntent.UnknownIntent -> OperationResult.Success(ExecutableAction.NoOpAction)
    }

    private suspend fun resolveAppLaunch(
        intent: LauncherIntent.LaunchAppIntent,
    ): OperationResult<ExecutableAction> {
        val appsResult = repository.getInstalledApps()
        if (appsResult is OperationResult.Failure) return appsResult

        val apps = (appsResult as OperationResult.Success).value

        // A4′ phase 3a (plan §0.2 B-3): a launch the device already pinned to a package opens exactly
        // that package. `LaunchAppIntent.packageName` existed unused until now; nothing but the bridge
        // sets it, so every other path below is byte-for-byte unchanged. Gone since the pin was made →
        // "not found" under the words the user typed, never a fallback to the label.
        intent.packageName?.let { pinned ->
            val app = apps.firstOrNull { it.packageName == pinned }
            return OperationResult.Success(
                if (app != null) {
                    ExecutableAction.LaunchAppAction(packageName = app.packageName, activityName = app.activityName)
                } else {
                    ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound(intent.displayNameQuery))
                },
            )
        }

        val query = intent.displayNameQuery.lowercase(Locale.ROOT)
        val matches = apps.filter { it.label.lowercase(Locale.ROOT) == query }

        return when {
            matches.size == 1 -> OperationResult.Success(
                ExecutableAction.LaunchAppAction(
                    packageName = matches[0].packageName,
                    activityName = matches[0].activityName,
                )
            )
            matches.size > 1 -> OperationResult.Success(
                ExecutableAction.AmbiguousAppAction(
                    query = intent.displayNameQuery,
                    candidates = matches,
                )
            )
            else -> OperationResult.Success(
                ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound(intent.displayNameQuery))
            )
        }
    }

    private fun resolveSimpleCommand(command: SimpleCommand): ExecutableAction = when (command) {
        SimpleCommand.SHOW_APPS -> ExecutableAction.ShowMessageAction(CommandMessage.ShowingAllApps)
        SimpleCommand.CLEAR -> ExecutableAction.NoOpAction
        SimpleCommand.HELP -> ExecutableAction.ShowMessageAction(CommandMessage.HelpBrief)
        SimpleCommand.OPEN_ASSISTANT -> ExecutableAction.ShowMessageAction(CommandMessage.AssistantComingSoon)
    }
}
