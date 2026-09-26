package com.sidr.launcher.domain.repository

import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult

interface InstalledAppsRepository {
    suspend fun getInstalledApps(): OperationResult<List<InstalledApp>>
}
