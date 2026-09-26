package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveAliasesUseCase(
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    fun observe(): Flow<List<AliasView>> = store.observeAll().map { aliases ->
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        val byPackage = installed.associateBy { it.packageName }
        aliases.mapNotNull { alias ->
            val packageName = alias.target.appPackageOrNull() ?: return@mapNotNull null
            val app = byPackage[packageName] ?: return@mapNotNull null
            AliasView(
                phrase = alias.phrase,
                targetPackageName = packageName,
                targetLabel = app.label,
            )
        }
    }
}
