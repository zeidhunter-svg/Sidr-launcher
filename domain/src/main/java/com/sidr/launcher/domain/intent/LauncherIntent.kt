package com.sidr.launcher.domain.intent

sealed interface LauncherIntent {

    data class LaunchAppIntent(
        val displayNameQuery: String,
        val packageName: String? = null,
    ) : LauncherIntent

    data class SearchIntent(
        val query: String,
        val target: SearchTarget = SearchTarget.WEB,
    ) : LauncherIntent

    data class OpenSettingsIntent(
        val destination: String? = null,
    ) : LauncherIntent

    data class SimpleCommandIntent(
        val command: SimpleCommand,
    ) : LauncherIntent

    /** Open a high-confidence, safe web address (AIL-2). [url] carries an explicit http(s) scheme. */
    data class OpenUrlIntent(
        val url: String,
    ) : LauncherIntent

    /** Search the Play Store for an app by name (AIL-2 — "install <app>"). */
    data class PlayStoreSearchIntent(
        val query: String,
    ) : LauncherIntent

    data class UnknownIntent(
        val originalInput: String,
        val reason: String? = null,
    ) : LauncherIntent
}

enum class SearchTarget { WEB, APP, LOCAL }

enum class SimpleCommand { SHOW_APPS, CLEAR, HELP, OPEN_ASSISTANT }
