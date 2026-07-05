package com.sidr.launcher.domain.action

/**
 * A concrete, routed action instance carrying its **semantic, still-unresolved** arguments — the value
 * type the AIL-4 router emits (`PlanResult.RoutedAction`) and the AIL-5 confirmation UI acts on.
 *
 * This is intentionally a **parallel** hierarchy to `ExecutableAction`, not a wrapper around it:
 * a routed [LaunchApp] carries the raw `query` ("telegram"), not a resolved package — resolution
 * (query → package via `IntentActionResolver`) and the mapping to `ExecutableAction` happen later, at
 * execution time (AIL-2/4/5). `ExecutableAction` stays the execution vocabulary and is untouched; the
 * registry lays above it. Every variant exposes its [id] so a proposal can be matched to its
 * [ActionDescriptor] in the [ActionCatalog].
 *
 * [OpenUrl] and [PlayStoreSearch] are the AIL-2 families — declared now so the vocabulary is complete;
 * their executor + rule recognition arrive in AIL-2.
 */
sealed interface LauncherAction {

    /** The registered family this instance belongs to. */
    val id: ActionId

    /** Launch an installed app matching [query] (unresolved display-name query, not a package). */
    data class LaunchApp(val query: String) : LauncherAction {
        override val id: ActionId get() = ActionIds.LAUNCH_APP
    }

    /** Run a web search for [query] via the configured search provider. */
    data class WebSearch(val query: String) : LauncherAction {
        override val id: ActionId get() = ActionIds.WEB_SEARCH
    }

    /** Open the launcher's own settings. */
    data object OpenSettings : LauncherAction {
        override val id: ActionId = ActionIds.OPEN_SETTINGS
    }

    /** Route to the assistant, optionally pre-filling [prompt]. */
    data class OpenAssistant(val prompt: String? = null) : LauncherAction {
        override val id: ActionId get() = ActionIds.OPEN_ASSISTANT
    }

    /** Show the full app grid. */
    data object ShowApps : LauncherAction {
        override val id: ActionId = ActionIds.SHOW_APPS
    }

    /** Open [url] in a browser (AIL-2). URL safety/normalization is enforced before this is built. */
    data class OpenUrl(val url: String) : LauncherAction {
        override val id: ActionId get() = ActionIds.OPEN_URL
    }

    /** Search/open the Play Store for [query] (AIL-2). */
    data class PlayStoreSearch(val query: String) : LauncherAction {
        override val id: ActionId get() = ActionIds.PLAY_STORE_SEARCH
    }
}
