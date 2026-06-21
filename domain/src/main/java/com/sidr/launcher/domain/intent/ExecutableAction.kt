package com.sidr.launcher.domain.intent

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
}
