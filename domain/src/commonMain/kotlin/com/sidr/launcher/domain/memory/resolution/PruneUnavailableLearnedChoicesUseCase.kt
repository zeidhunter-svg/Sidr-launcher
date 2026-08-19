package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first

class PruneUnavailableLearnedChoicesUseCase(
    private val store: ResolutionPreferenceStore,
    private val installedApps: InstalledAppsRepository,
) {
    /** Best-effort: delete preferences whose target app is no longer installed. Never throws. */
    suspend fun prune(): OperationResult<Unit> {
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
            .map { it.packageName }.toSet()
        store.observeAll().first().forEach { pref ->
            val pkg = pref.preferredTarget.appPackageOrNull() ?: return@forEach
            if (pkg !in installed) store.delete(pref.capabilityKey, pref.context)
        }
        return OperationResult.Success(Unit)
    }
}
