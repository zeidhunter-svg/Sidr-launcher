package com.sidr.launcher.core.common.navigation

/**
 * Type-safe route constants for navigation destinations.
 * These are plain strings/objects that can be used by both
 * app-level NavHost and feature ViewModels for NavigationEvent.
 */
sealed class Routes {
    object Launcher : Routes() {
        const val ROUTE = "launcher"
    }
    
    object Assistant : Routes() {
        const val ROUTE = "assistant"
    }
    
    object Settings : Routes() {
        const val ROUTE = "settings"
    }
    
    object PermissionEducation : Routes() {
        const val ROUTE = "permission_education"
    }
}
