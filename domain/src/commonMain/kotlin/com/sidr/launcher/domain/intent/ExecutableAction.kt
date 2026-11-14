package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.model.InstalledApp

sealed interface ExecutableAction {

    data class LaunchAppAction(
        val packageName: String,
        val activityName: String? = null,
    ) : ExecutableAction

    data class OpenSearchAction(
        val query: String,
        val target: SearchTarget,
    ) : ExecutableAction

    data object OpenLauncherSettingsAction : ExecutableAction

    /** Open a safe, high-confidence [url] in the browser (AIL-2). Normalization/safety is enforced upstream. */
    data class OpenUrlAction(val url: String) : ExecutableAction

    /** Search the Play Store for [query] (AIL-2); the executor tries market:// then a web fallback. */
    data class PlayStoreSearchAction(val query: String) : ExecutableAction

    data class ShowMessageAction(val message: CommandMessage) : ExecutableAction

    data object NoOpAction : ExecutableAction

    /**
     * Returned when a query matches 2+ apps, or — [confirm] (A4′ phase 3b, Task 7; B-2) — when it is only a
     * word FORM of one or more labels («миравину» for «Миравин»): those apps are offered, never launched.
     * Block D renders [candidates] as tappable suggestions; [query] is the name as the user wrote it.
     */
    data class AmbiguousAppAction(
        val query: String,
        val candidates: List<InstalledApp>,
        val confirm: Boolean = false,
    ) : ExecutableAction
}
