package com.sidr.launcher.domain.preferences

data class UserPreferences(
    val themeName: String = "system",          // "system" | "light" | "dark"
    val commandInputEnabled: Boolean = true,
    // Block X6 — deferred UI preferences.
    // Home "Favorites" row size (top-N most-used apps); read by LauncherViewModel.deriveFavorites.
    val favoritesCount: Int = 8,
    // Whether the mic affordance / voice-input path is offered. Gated together with recognizer
    // availability (isVoiceInputAvailable && micInputEnabled). Key is denylist-clean ("mic", not "voice").
    val micInputEnabled: Boolean = true,
    // One-shot first-run nudge: once the user dismisses (or acts on) the "set as default launcher"
    // hint, it never resurfaces. Detection of "am I default" is a runtime Android check in the UI.
    val setupHintDismissed: Boolean = false,
)
