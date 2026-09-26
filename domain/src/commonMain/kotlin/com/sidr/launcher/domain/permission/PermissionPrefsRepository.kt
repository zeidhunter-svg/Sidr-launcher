package com.sidr.launcher.domain.permission

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * Persists the per-feature "don't ask again / dismissed education" flag (Block G, Fork 5),
 * backed by DataStore (Block E). Per-feature granularity enforces the invariant that a denial
 * disables exactly one feature — dismissing wallpaper education never affects another feature.
 *
 * Only [PermissionFeature.requestable] features are persisted: a dormant feature has no request
 * flow, so it has no dismissed state. For a dormant feature [isDismissed] always emits `false`
 * and [setDismissed] is a no-op [OperationResult.Success].
 *
 * Reads return a [Flow]; writes return [OperationResult] and never throw to the caller.
 */
interface PermissionPrefsRepository {
    fun isDismissed(feature: PermissionFeature): Flow<Boolean>
    suspend fun setDismissed(feature: PermissionFeature, dismissed: Boolean): OperationResult<Unit>
}
