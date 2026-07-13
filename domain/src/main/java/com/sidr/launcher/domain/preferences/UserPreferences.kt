package com.sidr.launcher.domain.preferences

data class UserPreferences(
    val themeName: String = "system",          // "system" | "light" | "dark"
    // AIL-6 / DF-7, reactivated 2026-07-12 (owner decision) — brand theme choice. "grey" is the
    // soft-classic-grey default; "green"/"amber" are the historical AIL-0 identities, restored as
    // real, user-selectable full themes (each resolves its own ground/surface/text/dim/faint/accent
    // palette — see sidrColorsFor in core/ui SidrColors.kt; the Shahada (sacred) and status colours
    // are never affected). Stored as a plain string so :domain stays free of the core/ui AccentColor
    // enum; the string → enum mapping lives in :app (LauncherActivity).
    // Key is denylist-clean ("accent"/"color" are not forbidden terms).
    val accentColor: String = "grey",          // "grey" | "green" | "amber"
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
    // AIL-2 / R5 — configurable web-search provider (default Google). A URL template with a `{q}`
    // placeholder for the URL-encoded query; if the placeholder is absent, the encoded query is
    // appended. No switcher UI ships in AIL-2 (deferred to AIL-3/DF-7); the default retires the
    // former hardcoded-Google TODO in the executor. Persisted key is denylist-clean.
    val webProviderTemplate: String = "https://www.google.com/search?q={q}",
    // 2026-07-12 — when false (default) the bottom navigation chrome auto-hides after ~5s of idle on
    // each tab root and is summoned back via a thin handle (calm/minimal Home). When true the user
    // pins it permanently visible. Presentation-only; key is denylist-clean ("nav"/"bar" not forbidden).
    val alwaysShowNavBar: Boolean = false,
)
