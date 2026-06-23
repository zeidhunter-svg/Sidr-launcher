package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus

/**
 * In-memory fake for [PermissionChecker]. Reports [defaultStatus] unless a per-feature status is
 * set via [setStatus]. Not wired into any Hilt graph — use directly in unit tests.
 *
 * Note: the real Android checker only ever returns GRANTED/DENIED; this fake permits any
 * [PermissionStatus] so a test can also exercise refresh into PERMANENTLY_DENIED if needed.
 */
class FakePermissionChecker(
    var defaultStatus: PermissionStatus = PermissionStatus.DENIED,
) : PermissionChecker {

    private val statuses = mutableMapOf<PermissionFeature, PermissionStatus>()

    fun setStatus(feature: PermissionFeature, status: PermissionStatus) {
        statuses[feature] = status
    }

    override fun status(feature: PermissionFeature): PermissionStatus =
        statuses[feature] ?: defaultStatus
}
