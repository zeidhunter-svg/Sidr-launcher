package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult

/** The wrapped resolver, usually S2-1's ResolveCommandWithPreferenceUseCase. */
fun interface ResolvedCommandStep {
    suspend fun resolve(rawInput: String): ResolvedCommand
}

/**
 * Explicit-alias decorator (S2-2). It fills only the gap left by the rule/preference pipeline:
 * every non-Unknown or already-auto-resolved result passes through unchanged.
 *
 * **Since the A4′ acceptance tail — a fallback path; whether to delete it is the owner's call** (task 3), together
 * with `CommandFeedback.UnknownCommand` (`:feature:launcher`), which is dead in production online. An alias
 * is now read where the command is routed: the whole command at `RouteCommandUseCase` step 1a, an alias and its word
 * forms in FastPath's launch (`IntentActionResolver`, «открой tg»). This class answers only a [CommandOutcome.Unknown]
 * the route hands back, and the route no longer hands one back for a bare alias in any state (step 1a starts its
 * session first); in every state — since task 5 of the tail, for ANY input (local-only, no provider, offline and a model
 * that declines each answer with a `Message`; a decline is `UnderstoodNoAction`) — its `Unknown` branch is dead (plan
 * review M15). Kept, not deleted: an alias whose app the route could not launch still falls through to here unchanged.
 */
class ResolveCommandWithAliasUseCase(
    private val inner: ResolvedCommandStep,
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    suspend fun resolve(rawInput: String): ResolvedCommand {
        val resolved = inner.resolve(rawInput)
        if (resolved !is ResolvedCommand.Outcome) return resolved
        val unknown = resolved.outcome as? CommandOutcome.Unknown ?: return resolved

        val phrase = CommandNormalizer.normalize(rawInput)
        val alias = (store.find(phrase) as? OperationResult.Success)?.value ?: return resolved
        val packageName = alias.target.appPackageOrNull() ?: return resolved

        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        if (installed.none { it.packageName == packageName }) return resolved

        return ResolvedCommand.AutoLaunch(
            target = ResolvedTarget.App(packageName),
            fallback = unknown,
        )
    }
}
