package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class PruneUnavailableAliasesUseCase(
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    /** Best-effort: delete aliases whose target app is no longer installed. Never throws. */
    suspend fun prune(): OperationResult<Unit> =
        try {
            val installed = (installedApps.getInstalledApps() as? OperationResult.Success)
                ?.value
                .orEmpty()
                .map { it.packageName }
                .toSet()
            store.observeAll().first().forEach { alias ->
                val packageName = alias.target.appPackageOrNull() ?: return@forEach
                if (packageName !in installed) store.delete(alias.phrase)
            }
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OperationResult.Success(Unit)
        }
}
