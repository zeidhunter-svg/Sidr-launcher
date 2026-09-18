package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.appPackageOrNull
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import javax.inject.Inject

/**
 * A name becomes a package, or nothing happens. Task 6 (A1″ Phase 3a) — this feeds `uninstall_app`,
 * the track's first `CONFIRM` tool over an irreversible act, so `null` is not "no answer yet"; it is
 * the resolver's own considered refusal, and every caller must treat it as such. Never guesses: two
 * installed apps sharing a label, no match at all, a blank query, and a repository failure all decline
 * the same way — silently, never by throwing.
 *
 * **Learned resolutions are deliberately not consulted, and this is a safety argument, not a scope
 * cut.** A learned choice is keyed by `CapabilityKey(ActionIds.LAUNCH_APP, query)` — consent the user
 * gave for *opening* an app. Reusing that preference to choose a target for `uninstall_app` would
 * transfer consent given for one act to a different, irreversible one. Aliases are different: an alias
 * is an explicit naming of an app (`set_app_alias`), not a preference about which action to take, so it
 * is consulted here and wins over a bare label match.
 *
 * **An alias outlives its target.** The app an alias names can be uninstalled while the alias row
 * stays, so a package resolved from an alias is only accepted if it is still in the installed list —
 * otherwise this would send an uninstall intent at nothing and the caller would report an effect over
 * emptiness (review finding M3, spec §7.6/§7.7).
 */
class AppTargetResolver @Inject constructor(
    private val apps: InstalledAppsRepository,
    private val aliases: AliasStore,
) {
    suspend fun resolve(query: String): String? {
        val normalized = CommandNormalizer.normalize(query)
        if (normalized.isBlank()) return null

        val installed = (apps.getInstalledApps() as? OperationResult.Success)?.value ?: return null

        (aliases.find(normalized) as? OperationResult.Success)?.value?.target?.appPackageOrNull()
            ?.takeIf { pkg -> installed.any { it.packageName == pkg } }
            ?.let { return it }

        return installed
            .filter { CommandNormalizer.normalize(it.label) == normalized }
            .singleOrNull()
            ?.packageName
    }
}
