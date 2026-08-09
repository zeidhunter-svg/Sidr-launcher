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

    /** Dedicated AI-provider setup surface (base URL / model / API key). Reached from Settings; kept
     *  separate from the [Assistant] chat so the two never overlap. */
    object AssistantProvider : Routes() {
        const val ROUTE = "assistant_provider"
    }

    object LearnedChoices : Routes() {
        const val ROUTE = "learned_choices"
    }

    object Aliases : Routes() {
        const val ROUTE = "aliases"
    }

    /**
     * Prayer setup (DS-6B Task 8): method/madhab/location choice. Reached from Settings; a pushed,
     * unwrapped destination like [LearnedChoices]/[Aliases] (bottom tab bar hides on push).
     */
    object PrayerSettings : Routes() {
        const val ROUTE = "prayer_settings"
    }

    /**
     * Prayer detail (DS-6B Task 8): the five prayers + Sunrise + provenance/freshness/method/madhab/
     * location, linking back to [PrayerSettings]. Reached from the (Task 9) Home strip.
     */
    object PrayerDetail : Routes() {
        const val ROUTE = "prayer_detail"
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

    // ── Vision MVP preview tab roots (Task 7) ───────────────────────────────
    // Four additive, non-functional preview destinations behind the new app-level bottom tab bar.
    // Each is a plain tab root (no nav args) — Tasks 8–11 fill in their real screens later.

    object Tasks : Routes() {
        const val ROUTE = "preview_tasks"
    }

    object Agents : Routes() {
        const val ROUTE = "preview_agents"
    }

    object Activity : Routes() {
        const val ROUTE = "preview_activity"
    }

    object Terminal : Routes() {
        const val ROUTE = "preview_terminal"
    }

    /**
     * Interaction-moment previews (Task 12): a pushed, non-tab destination reachable from the
     * Tasks preview footer. Static samples of the Result / Partial / Error command-outcome
     * moments — no args needed.
     */
    object Moments : Routes() {
        const val ROUTE = "preview_moments"
    }
}
