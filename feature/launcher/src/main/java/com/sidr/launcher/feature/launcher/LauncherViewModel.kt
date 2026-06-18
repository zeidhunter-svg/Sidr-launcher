package com.sidr.launcher.feature.launcher

import androidx.lifecycle.ViewModel
import com.sidr.launcher.core.common.navigation.NavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * ViewModel for the Launcher destination.
 *
 * Navigation is expressed as [NavigationEvent] values emitted through [navigationEvents].
 * This ViewModel does NOT hold a reference to NavHostController — the app-level NavHost
 * collects the flow and performs the actual navigation.
 *
 * Business logic is added in later phases; this class exists to prove the
 * navigation-event plumbing end-to-end.
 */
@HiltViewModel
class LauncherViewModel @Inject constructor() : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    /** Called from the UI to request navigation to [route]. */
    fun navigateTo(route: String) {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(route))
    }

    /** Called from the UI to request going back. */
    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }
}
