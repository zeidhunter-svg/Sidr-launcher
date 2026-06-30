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

        /** Optional nav arg naming the [com.sidr.launcher.domain.permission.PermissionFeature] to educate. */
        const val ARG_FEATURE = "feature"

        /** Full pattern registered in the NavHost; the arg is optional (defaults to WALLPAPER). */
        const val ROUTE_WITH_ARG = "$ROUTE?$ARG_FEATURE={$ARG_FEATURE}"

        /** Build a concrete route for a feature, e.g. `routeFor("VOICE_INPUT")`. */
        fun routeFor(feature: String): String = "$ROUTE?$ARG_FEATURE=$feature"
    }
}
