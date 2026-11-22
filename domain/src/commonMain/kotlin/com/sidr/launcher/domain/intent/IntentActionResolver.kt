package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.appPackageOrNull
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.AppNameMatcher
import com.sidr.launcher.domain.text.NameMatch
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.text.TextFolding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

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
 *
 * **An alias is read first** (A4′ acceptance tail, task 3; owner decision 3, controller ruling K10). An alias is the
 * user's explicit naming of an app (`set_app_alias`, the aliases screen), so it answers before a label, as in
 * `AppTargetResolver` (step 2b, the bridge): an exact alias before an exact label on every path. **What a FORM of an
 * alias does is NOT the same on every path, and that asymmetry is named, not hidden:** here it launches (3 below), and
 * a form of an alias that meets another app's EXACT label is a choice of both; in `AppTargetResolver` (`wholeName`) an
 * exact label binds before any form of an alias is looked at, and a form of an alias is only offered (`Confirm`) —
 * that resolver feeds `uninstall_app`, and task 3 left it unchanged (an owner question, task 3's report). The order
 * here, over [aliases] and the installed apps, an alias counting only while its app is installed:
 *  1. the alias stored under exactly these words, or one the words name exactly after the [folding] — launches
 *     («открой tg»; «камера» as an alias opens its app, not the app labelled «Камера»); several aliases' apps are a
 *     choice;
 *  2. an exact label, as before — unless a FORM of an alias also answers (3);
 *  3. a word FORM of an alias («телегу» for «телега») **launches** — owner 2026-11-21: «алиасы не спрашивать — лишний
 *     слой» — when no label of another app answers too; when one does (its form or the very label), the alias's app
 *     and that app are offered as a choice ([ExecutableAction.AmbiguousAppAction], `confirm` false), nothing guessed;
 *  4. a word form of a label alone is offered for confirmation, as before (B-2);
 *  5. else «нет такого приложения».
 * A pinned launch (below) reads no alias. **An unreadable alias store leaves the launch to the labels** — what
 * FastPath answered before it read aliases at all: a `find` that fails is «no alias under these words», a list that
 * throws is «no aliases». That is NOT `AppTargetResolver`'s rule (it answers `Unavailable`, ruling E4-F1, because it
 * feeds an irreversible tool); a launch is `SAFE`, and a broken alias table does not stop every typed launch.
 */
class IntentActionResolver(
    private val repository: InstalledAppsRepository,
    private val folding: TextFolding = PlainTextFolding,
    // A4′ acceptance tail, task 3: `NONE` by default, so every construction site that predates it resolves exactly as
    // before; the production provider passes the device's store (`IntentProvidesModule.provideIntentActionResolver`).
    private val aliases: AliasStore = AliasStore.NONE,
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
        // that package. `LaunchAppIntent.packageName` existed unused until then; only the device sets it —
        // the bridge's binder (`DeviceMentionBinder`, also on a learned replay) and, since task 3 of the A4′
        // acceptance tail, the route's step 1a (a bare alias) — so every unpinned launch takes the name
        // match below. Gone since the pin was made →
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

        return OperationResult.Success(byName(intent.displayNameQuery, apps))
    }

    /** The order of the class KDoc: an alias exactly, a label exactly, a form of an alias, a form of a label, none. */
    private suspend fun byName(query: String, apps: List<InstalledApp>): ExecutableAction {
        // (1) The alias stored under exactly these words — while its app is installed.
        storedAliasPackage(CommandNormalizer.normalize(query))
            ?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
            ?.let { return launch(it) }

        // (1) …or an alias the words name exactly after the folding (a stress mark the stored key does not carry).
        val byAlias = AppNameMatcher.matchWhole(query, aliasNamed(apps), folding)
        if (byAlias is NameMatch.Exact) return launchOrChoose(query, installedTargetsOf(byAlias.apps, apps))

        val byLabel = AppNameMatcher.matchWhole(query, apps, folding)

        // (3) A form of an alias launches its app — unless a label of ANOTHER app answers too: then both are a choice.
        if (byAlias is NameMatch.Edge) {
            val aliased = installedTargetsOf(byAlias.apps, apps)
            val others = appsOf(byLabel).filter { label -> aliased.none { it.packageName == label.packageName } }
            return if (others.isEmpty()) {
                launchOrChoose(query, aliased)
            } else {
                ExecutableAction.AmbiguousAppAction(query = query, candidates = aliased + others)
            }
        }

        // (2), (4), (5) — labels alone, exactly as before task 3.
        return when (byLabel) {
            is NameMatch.Exact -> launchOrChoose(query, byLabel.apps)
            is NameMatch.Edge -> ExecutableAction.AmbiguousAppAction(query = query, candidates = byLabel.apps, confirm = true)
            NameMatch.None -> ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound(query))
        }
    }

    private fun launch(app: InstalledApp): ExecutableAction =
        ExecutableAction.LaunchAppAction(packageName = app.packageName, activityName = app.activityName)

    /** One app launches; several are the user's choice — never one picked. */
    private fun launchOrChoose(query: String, apps: List<InstalledApp>): ExecutableAction =
        if (apps.size == 1) launch(apps.single()) else ExecutableAction.AmbiguousAppAction(query = query, candidates = apps)

    /** The package the alias stored under [phrase] names, or `null` — none, a non-app target, or a read that failed. */
    private suspend fun storedAliasPackage(phrase: String): String? =
        (aliases.find(phrase) as? OperationResult.Success)?.value?.target?.appPackageOrNull()

    /**
     * Every alias whose app is installed, as an app labelled by the alias's PHRASE — so the one matcher compares
     * aliases and labels alike (`AppTargetResolver`'s shape). A list that throws is no aliases (class KDoc).
     */
    private suspend fun aliasNamed(apps: List<InstalledApp>): List<InstalledApp> {
        val all: List<Alias> = try {
            aliases.observeAll().first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }
        return all.mapNotNull { alias ->
            alias.target.appPackageOrNull()
                ?.takeIf { pkg -> apps.any { it.packageName == pkg } }
                ?.let { pkg -> InstalledApp(packageName = pkg, label = alias.phrase) }
        }
    }

    /** Alias pseudo-apps back to the installed apps they name, one per package, in the aliases' order. */
    private fun installedTargetsOf(named: List<InstalledApp>, apps: List<InstalledApp>): List<InstalledApp> =
        named.map { it.packageName }.distinct().mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

    private fun appsOf(match: NameMatch): List<InstalledApp> = when (match) {
        is NameMatch.Exact -> match.apps
        is NameMatch.Edge -> match.apps
        NameMatch.None -> emptyList()
    }

    private fun resolveSimpleCommand(command: SimpleCommand): ExecutableAction = when (command) {
        SimpleCommand.SHOW_APPS -> ExecutableAction.ShowMessageAction(CommandMessage.ShowingAllApps)
        SimpleCommand.CLEAR -> ExecutableAction.NoOpAction
        SimpleCommand.HELP -> ExecutableAction.ShowMessageAction(CommandMessage.HelpBrief)
        SimpleCommand.OPEN_ASSISTANT -> ExecutableAction.ShowMessageAction(CommandMessage.AssistantComingSoon)
    }
}
