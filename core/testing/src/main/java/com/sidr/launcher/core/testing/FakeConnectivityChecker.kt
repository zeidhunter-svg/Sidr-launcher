package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [ConnectivityChecker], backed by a [MutableStateFlow] so [isOnline] and
 * [connectivity] stay consistent. Set [online] to flip reachability in a test. Not wired into any
 * Hilt graph — use directly in unit tests.
 */
class FakeConnectivityChecker(initiallyOnline: Boolean = true) : ConnectivityChecker {

    private val state = MutableStateFlow(initiallyOnline)

    var online: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun isOnline(): Boolean = state.value

    override val connectivity: Flow<Boolean> = state.asStateFlow()
}
