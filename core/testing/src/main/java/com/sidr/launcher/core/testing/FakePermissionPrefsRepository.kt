package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionPrefsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for [PermissionPrefsRepository], backed by a per-feature [MutableStateFlow] so
 * reads observe writes. Records every [setDismissed] call for assertions. Not wired into any Hilt
 * graph — use directly in unit tests.
 */
class FakePermissionPrefsRepository : PermissionPrefsRepository {

    private val flows = mutableMapOf<PermissionFeature, MutableStateFlow<Boolean>>()

    /** When non-null, [setDismissed] returns [OperationResult.Failure] and does not mutate state. */
    var errorToReturn: OperationError? = null

    val setCalls = mutableListOf<Pair<PermissionFeature, Boolean>>()

    private fun flowFor(feature: PermissionFeature): MutableStateFlow<Boolean> =
        flows.getOrPut(feature) { MutableStateFlow(false) }

    override fun isDismissed(feature: PermissionFeature): Flow<Boolean> =
        flowFor(feature).asStateFlow()

    override suspend fun setDismissed(
        feature: PermissionFeature,
        dismissed: Boolean,
    ): OperationResult<Unit> {
        setCalls += feature to dismissed
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            flowFor(feature).value = dismissed
            OperationResult.Success(Unit)
        }
    }
}
