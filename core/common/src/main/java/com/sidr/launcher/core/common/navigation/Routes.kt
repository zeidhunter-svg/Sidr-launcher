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

        /** Optional nav arg carrying an initial prompt to prefill the assistant input (Block X6-C). */
        const val ARG_PROMPT = "prompt"

        /** Full pattern registered in the NavHost; the arg is optional (bare `assistant` still matches). */
        const val ROUTE_WITH_ARG = "$ROUTE?$ARG_PROMPT={$ARG_PROMPT}"

        /**
         * Build a concrete route carrying an initial prompt. [encodedPrompt] must already be
         * URL-encoded by the caller (Android `Uri.encode`), so spaces/`?`/`&` survive the route parse
         * and are decoded back by Navigation. The prompt is consumed once on entry and NEVER persisted
         * (the assistant deliberately holds no SavedStateHandle — key/prompt stay transient).
         */
        fun routeFor(encodedPrompt: String): String = "$ROUTE?$ARG_PROMPT=$encodedPrompt"
    }
    
    object Settings : Routes() {
        const val ROUTE = "settings"
    }

    /**
     * The App Drawer (full installed-apps list). Registered as a real destination in Block X3;
     * the home's "All apps" affordance navigates here already in X2, so until X3 wires the
     * `composable(...)`, [com.sidr.launcher.core.common.navigation.NavigationEvent] safe-fallback
     * returns to [Launcher] (acceptable interim behaviour).
     */
    object AppDrawer : Routes() {
        const val ROUTE = "app_drawer"
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
