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
