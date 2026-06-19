package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult

/**
 * Configurable fake for unit-testing ViewModels and use cases that depend on
 * [InstalledAppsRepository]. Not wired into any Hilt graph — use directly in tests.
 */
class FakeInstalledAppsRepository : InstalledAppsRepository {

    /** Set before the test to control what [getInstalledApps] returns. */
    var appsToReturn: List<InstalledApp> = emptyList()

    /** When non-null, [getInstalledApps] returns [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    /** Number of times [getInstalledApps] has been called — useful for assertion. */
    var callCount: Int = 0
        private set

    override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> {
        callCount++
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            OperationResult.Success(appsToReturn)
        }
    }

    fun reset() {
        appsToReturn = emptyList()
        errorToReturn = null
        callCount = 0
    }
}
