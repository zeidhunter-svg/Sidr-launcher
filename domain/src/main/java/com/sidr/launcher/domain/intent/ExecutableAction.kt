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

    data class ShowMessageAction(val message: String) : ExecutableAction

    data object NoOpAction : ExecutableAction

    /** Returned when a query matches 2+ apps; Block D renders candidates as tappable suggestions. */
    data class AmbiguousAppAction(
        val query: String,
        val candidates: List<InstalledApp>,
    ) : ExecutableAction
}
