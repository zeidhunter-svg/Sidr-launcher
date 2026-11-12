package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.AppNameMatcher
import com.sidr.launcher.domain.text.NameMatch
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.text.TextFolding

/**
 * Use case: maps a [LauncherIntent] to an [ExecutableAction].
 *
 * Normal resolution outcomes (not found, ambiguous) are returned as [OperationResult.Success]
 * with an appropriate action — they are not errors. [OperationResult.Failure] is reserved for
 * technical failures (e.g. repository I/O failure).
 *
 * **An app name is matched by the one name matcher** (A4′ phase 3b, Task 7; forks 3b-D/3b-G «all paths»,
 * B-2): [AppNameMatcher.matchWhole] over [folding] — the device's one [TextFolding] in production
 * (`IntentProvidesModule`), the domain's [PlainTextFolding] by default. The WHOLE name, never its windows:
 * those are step 2b's alone (§0.1 refinement), and phase 1's owner fork F1-2 holds here — «lumora studio»
 * with only «Lumora» installed is still «no such app», because the extra words may name another app. One
 * exact label launches; several are a choice; a word FORM of one or more labels («миравину» for
 * «Миравин») is only ever offered, never launched — [ExecutableAction.AmbiguousAppAction.confirm].
 */
class IntentActionResolver(
    private val repository: InstalledAppsRepository,
    private val folding: TextFolding = PlainTextFolding,
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
        // that package. `LaunchAppIntent.packageName` existed unused until then; nothing but the bridge
        // sets it, so every unpinned launch takes the name match below. Gone since the pin was made →
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

        return OperationResult.Success(
            when (val match = AppNameMatcher.matchWhole(intent.displayNameQuery, apps, folding)) {
                is NameMatch.Exact -> if (match.apps.size == 1) {
                    ExecutableAction.LaunchAppAction(
                        packageName = match.apps[0].packageName,
                        activityName = match.apps[0].activityName,
                    )
                } else {
                    ExecutableAction.AmbiguousAppAction(query = intent.displayNameQuery, candidates = match.apps)
                }
                is NameMatch.Edge -> ExecutableAction.AmbiguousAppAction(
                    query = intent.displayNameQuery,
                    candidates = match.apps,
                    confirm = true,
                )
                NameMatch.None -> ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound(intent.displayNameQuery))
            },
        )
    }

    private fun resolveSimpleCommand(command: SimpleCommand): ExecutableAction = when (command) {
        SimpleCommand.SHOW_APPS -> ExecutableAction.ShowMessageAction(CommandMessage.ShowingAllApps)
        SimpleCommand.CLEAR -> ExecutableAction.NoOpAction
        SimpleCommand.HELP -> ExecutableAction.ShowMessageAction(CommandMessage.HelpBrief)
        SimpleCommand.OPEN_ASSISTANT -> ExecutableAction.ShowMessageAction(CommandMessage.AssistantComingSoon)
    }
}
