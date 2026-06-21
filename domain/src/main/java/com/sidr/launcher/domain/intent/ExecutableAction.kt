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

    data class ShowMessageAction(val message: String) : ExecutableAction

    data object NoOpAction : ExecutableAction

    /** Returned when a query matches 2+ apps; Block D renders candidates as tappable suggestions. */
    data class AmbiguousAppAction(
        val query: String,
        val candidates: List<InstalledApp>,
    ) : ExecutableAction
}
