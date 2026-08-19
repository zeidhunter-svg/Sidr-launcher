package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveLearnedChoicesUseCase(
    private val store: ResolutionPreferenceStore,
    private val installedApps: InstalledAppsRepository,
    private val displayState: EvaluateLearnedChoiceDisplayStateUseCase,
) {
    fun observe(): Flow<List<LearnedChoiceView>> = store.observeAll().map { prefs ->
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        val byPkg = installed.associateBy { it.packageName }
        prefs.mapNotNull { pref ->
            val pkg = pref.preferredTarget.appPackageOrNull() ?: return@mapNotNull null
            val app = byPkg[pkg] ?: return@mapNotNull null // filter uninstalled
            LearnedChoiceView(
                capabilityKey = pref.capabilityKey,
                targetPackageName = pkg,
                targetLabel = app.label,
                // v1 scope = LAUNCH_APP (SAFE); currentCandidates unknown on this screen → AutoReady/Learning.
                displayState = displayState.evaluate(pref, currentCandidates = null, ActionRiskLevel.SAFE, targetInstalled = true),
            )
        }
    }
}
