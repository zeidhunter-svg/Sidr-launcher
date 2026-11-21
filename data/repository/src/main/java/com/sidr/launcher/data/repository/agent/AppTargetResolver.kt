package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.appPackageOrNull
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.AppNameMatcher
import com.sidr.launcher.domain.text.NameMatch
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ToolDurability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What a name resolves to on this device (A4′ phase 3a, spec §7.12.3 condition 2). Until then
 * [AppTargetResolver.resolve] folded "two candidates", "none", "blank" and "the list could not be read"
 * into one `null`; the bridge must tell them apart — two candidates are a choice for the user, none is
 * an honest "not found", a failed read is neither. Since phase 3b (fork 3b-G) a word FORM of a name is a
 * fifth answer, [Confirm]: the apps it may mean, offered for the user's confirmation and never bound.
 */
sealed interface AppCandidates {
    data object None : AppCandidates

    /**
     * One app answers exactly. [foldedOnly] — exact only AFTER the text folding (`TextFolding`, B-1), not as
     * the words were typed (`CommandNormalizer` forms differ: «lumora» for «Lumora+»). Owner decision
     * 2026-11-17: for an irreversible tool such an answer is a pick, never a bind ([forIrreversible]).
     */
    data class One(val app: InstalledApp, val foldedOnly: Boolean = false) : AppCandidates
    data class Several(val apps: List<InstalledApp>) : AppCandidates
    data class Confirm(val apps: List<InstalledApp>) : AppCandidates
    data object Unavailable : AppCandidates
}

/**
 * Owner decision 2026-11-17 (phase 3b close, final review B-I1, answer «2: a» — narrow entry (a)): for a
 * tool whose act cannot be undone (`ToolDurability.DURABLE` — `uninstall_app` today, the only app-sorted
 * one), a name that is exact only after folding is OFFERED as a one-app confirmation (`confirm = true` on
 * step 2b, `AppChoice.confirm` on the bridge's card) and bound only by the user's tap; an answer exact as
 * typed binds as before. Every other tool keeps the folded exact bind. Read at both binding sites —
 * `ToolMatchPlanner` (2b) and `DeviceMentionBinder` (the bridge) — so the two cannot disagree.
 */
fun AppCandidates.forIrreversible(irreversible: Boolean): AppCandidates =
    if (irreversible && this is AppCandidates.One && foldedOnly) AppCandidates.Confirm(listOf(app)) else this

/**
 * The ONE predicate both binding sites read for [forIrreversible] (final review m4): anything but
 * `TRANSIENT` — `DURABLE`, a value added later, or no descriptor at all — is irreversible, the stricter reading.
 */
fun ToolDurability?.isIrreversible(): Boolean = this != ToolDurability.TRANSIENT

/**
 * A name becomes a package, or nothing happens. Task 6 (A1″ Phase 3a) — this feeds `uninstall_app`,
 * the track's first `CONFIRM` tool over an irreversible act, so [resolve]'s `null` is not "no answer
 * yet"; it is the resolver's own considered refusal, and every caller must treat it as such. Never
 * guesses: only an EXACT name binds (an alias, or a whole label under [AppNameMatcher]; for an irreversible
 * tool, exact as typed — but THIS class does not know the tool: its callers apply [forIrreversible], owner
 * decision 2026-11-17); two apps
 * answering exactly are the user's choice; a word form (phase 3b, B-2 — «миравину» for «Миравин») is
 * only offered ([AppCandidates.Confirm]); no match, a blank query and a read failure decline — and
 * [resolve] declines all of these the same way, silently, never by throwing.
 *
 * **Learned resolutions are deliberately not consulted, and this is a safety argument, not a scope
 * cut.** A learned choice is keyed by `LearnedChoiceKeys.launch(name, folding)` — a `launch_app` key,
 * consent the user gave for *opening* an app. Reusing that preference to choose a target for `uninstall_app` would
 * transfer consent given for one act to a different, irreversible one. Aliases are different: an alias
 * is an explicit naming of an app (`set_app_alias`), not a preference about which action to take, so it
 * is consulted here and wins over a bare label match — exactly; a form of an alias («кварцелота») is
 * offered like a form of a label.
 *
 * **An alias outlives its target.** The app an alias names can be uninstalled while the alias row
 * stays, so a package resolved from an alias is only accepted if it is still in the installed list —
 * otherwise this would send an uninstall intent at nothing and the caller would report an effect over
 * emptiness (review finding M3, spec §7.6/§7.7).
 *
 * **An alias `find` that fails is [AppCandidates.Unavailable]**, not "no alias": an alias may name a
 * different app than the label does, so answering from labels alone would be a guess. That holds for
 * `find` only. Caveat E4-F2: the production alias list (`AliasStoreImpl.observeAll`) swallows its errors
 * and emits an empty list. So when the list read fails in production, word forms of aliases silently
 * vanish — nothing is offered for them. Exact aliases still bind through `find`. The `observeAll`-throws
 * branch below answers `Unavailable` only for a store that does throw.
 */
class AppTargetResolver @Inject constructor(
    private val apps: InstalledAppsRepository,
    private val aliases: AliasStore,
    private val folding: TextFolding,
) {
    /**
     * Every candidate the WHOLE name can mean, never a guess (the bridge's mention, the sort resolver):
     * an alias exactly → [AppCandidates.One]; else a label exactly → one or [AppCandidates.Several]; else
     * a form of an alias or of a label → [AppCandidates.Confirm]; else [AppCandidates.None].
     */
    suspend fun candidates(query: String): AppCandidates {
        val normalized = CommandNormalizer.normalize(query)
        if (normalized.isBlank()) return AppCandidates.None
        val installed = installedOrNull() ?: return AppCandidates.Unavailable
        return wholeName(normalized, installed)
    }

    /**
     * What a name the MODEL wrote can mean (amendment D1, ADR «2026-11-22 — D1, поправка: имя приложения,
     * исправленное моделью»; `DeviceMentionBinder`): [candidates] with the word forms taken away — an alias
     * exactly, or a whole label exactly after the folding; a form of either ([AppCandidates.Confirm]) is
     * [AppCandidates.None], since the forms exist to meet the USER's words («миравину» for «Миравин»), never the
     * model's. One answer is never typed as the user typed it — the user did not type it at all — so it is
     * [AppCandidates.One.foldedOnly], and an irreversible tool makes it a pick ([forIrreversible], owner decision
     * 2026-11-17), never a bind.
     */
    suspend fun exactly(name: String): AppCandidates = when (val found = candidates(name)) {
        is AppCandidates.Confirm -> AppCandidates.None
        is AppCandidates.One -> found.copy(foldedOnly = true)
        else -> found
    }

    /**
     * Step 2b's search (phase 3b, fork 3b-E as refined in §0.1), read by `ToolMatchPlanner`: the whole
     * slot first, exactly as [candidates]; if nothing answers it, the longest windows of its words that
     * match a label ([AppNameMatcher.matchWindows]). **A window short of the slot is never
     * [AppCandidates.One]** — the extra words may name another app (phase 1's F1-2) — so it is a
     * [AppCandidates.Confirm], as is any form. Every answer but [AppCandidates.One] reaches the user on the
     * device (`PlanningResult.Unresolved`), never the model. **Aliases answer the whole slot only**
     * (controller ruling E5-P5, a named limit, not built): «телега пожалуйста» does not reach the alias
     * «телега» through a window — it is answered from labels, and an alias-only name inside extra words is
     * «not found».
     */
    suspend fun windows(slot: String): AppCandidates {
        val normalized = CommandNormalizer.normalize(slot)
        if (normalized.isBlank()) return AppCandidates.None
        val installed = installedOrNull() ?: return AppCandidates.Unavailable
        val whole = wholeName(normalized, installed)
        if (whole != AppCandidates.None) return whole
        val windowed = AppNameMatcher.matchWindows(normalized, installed, folding)
        return when (val match = windowed.match) {
            is NameMatch.Exact -> when {
                !windowed.whole -> AppCandidates.Confirm(match.apps)
                match.apps.size == 1 -> match.apps.single().let { AppCandidates.One(it, foldedOnly = !typedExactly(it, normalized)) }
                else -> AppCandidates.Several(match.apps)
            }
            is NameMatch.Edge -> AppCandidates.Confirm(match.apps)
            NameMatch.None -> AppCandidates.None
        }
    }

    /**
     * One package or nothing — the shape step 2b declined on until phase 3b, when `ToolMatchPlanner` moved
     * to [windows] so that a decline reaches the user instead of the model. No production caller is left;
     * kept for its tests and the shape it pins. A form binds nothing. It knows no tool and drops
     * [AppCandidates.One.foldedOnly]: a fold-only match resolves here — never feed an irreversible tool from it.
     */
    suspend fun resolve(query: String): String? =
        (candidates(query) as? AppCandidates.One)?.app?.packageName

    /**
     * Whether [packageName] — a package bound earlier, with no words kept to search by — is still
     * installed (A4′ phase 3b, Task 11: `DeviceMentionBinder.rebind` for `open_app_info`, whose step keeps
     * the package only). `null` = the app list could not be read: not «gone», which would be a lie.
     */
    suspend fun stillInstalled(packageName: String): Boolean? =
        installedOrNull()?.any { it.packageName == packageName }

    private suspend fun installedOrNull(): List<InstalledApp>? =
        (apps.getInstalledApps() as? OperationResult.Success)?.value

    private suspend fun wholeName(normalized: String, installed: List<InstalledApp>): AppCandidates {
        val stored = when (val found = aliases.find(normalized)) {
            is OperationResult.Success -> found.value
            is OperationResult.Failure -> return AppCandidates.Unavailable
        }
        stored?.target?.appPackageOrNull()
            ?.let { pkg -> installed.firstOrNull { it.packageName == pkg } }
            ?.let { return AppCandidates.One(it) }

        val named = aliasNamedOrNull(installed) ?: return AppCandidates.Unavailable
        val byAlias = AppNameMatcher.matchWhole(normalized, named, folding)
        if (byAlias is NameMatch.Exact) {
            return listed(installedTargetsOf(byAlias.apps, installed), foldedOnly = byAlias.apps.none { typedExactly(it, normalized) })
        }

        val byLabel = AppNameMatcher.matchWhole(normalized, installed, folding)
        if (byLabel is NameMatch.Exact) return listed(byLabel.apps, foldedOnly = byLabel.apps.none { typedExactly(it, normalized) })

        val forms = (installedTargetsOf(appsOf(byAlias), installed) + appsOf(byLabel)).distinctBy { it.packageName }
        return if (forms.isEmpty()) AppCandidates.None else AppCandidates.Confirm(forms)
    }

    /**
     * Every alias whose target is still installed, as an app labelled by the alias's PHRASE — so one
     * matcher compares aliases and labels alike. `null` = the aliases could not be read.
     */
    private suspend fun aliasNamedOrNull(installed: List<InstalledApp>): List<InstalledApp>? {
        val all: List<Alias> = try {
            aliases.observeAll().first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        return all.mapNotNull { alias ->
            alias.target.appPackageOrNull()
                ?.takeIf { pkg -> installed.any { it.packageName == pkg } }
                ?.let { pkg -> InstalledApp(packageName = pkg, label = alias.phrase) }
        }
    }

    /** Alias pseudo-apps back to the installed apps they name, one per package. */
    private fun installedTargetsOf(named: List<InstalledApp>, installed: List<InstalledApp>): List<InstalledApp> =
        named.map { it.packageName }.distinct().mapNotNull { pkg -> installed.firstOrNull { it.packageName == pkg } }

    private fun appsOf(match: NameMatch): List<InstalledApp> = when (match) {
        is NameMatch.Exact -> match.apps
        is NameMatch.Edge -> match.apps
        NameMatch.None -> emptyList()
    }

    /** Exact answers: one binds ([foldedOnly] kept for [forIrreversible]), several are the user's choice. */
    private fun listed(apps: List<InstalledApp>, foldedOnly: Boolean): AppCandidates =
        if (apps.size == 1) AppCandidates.One(apps.single(), foldedOnly) else AppCandidates.Several(apps)

    /**
     * Whether [app]'s label — an alias pseudo-app's label is its phrase — names the words exactly AS TYPED, in
     * the `CommandNormalizer` form the phase's base compared (owner decision 2026-11-17); otherwise an exact
     * [AppNameMatcher] answer is exact only after folding.
     */
    private fun typedExactly(app: InstalledApp, normalized: String): Boolean =
        CommandNormalizer.normalize(app.label) == normalized
}
