package com.sidr.launcher.core.common.navigation

/**
 * Navigation intent emitted by a ViewModel and collected by the app-level NavHost.
 * ViewModels must NOT hold a NavHostController reference — they emit NavigationEvent
 * through a Flow, and the app module performs the actual navigation.
 */
sealed interface NavigationEvent {
    /** Navigate to the given [route] string (use Routes constants). */
    data class NavigateTo(val route: String) : NavigationEvent

    /** Pop the back stack (go back). */
    data object NavigateBack : NavigationEvent
}
