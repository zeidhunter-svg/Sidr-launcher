package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.suggestions.Suggestion

data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    // Block X2: the small top-N most-used set rendered on the decluttered home. Derived from usage
    // history ∩ installed apps; the full [apps] list stays loaded (drawer + suggestion resolution).
    val favorites: List<InstalledApp> = emptyList(),
    // Block X6: whether the user has dismissed (or acted on) the first-run "set as default launcher"
    // nudge. The nudge shows on home only when this is false AND the launcher is not already default
    // (the default-launcher check is a runtime Android query performed in the screen).
    val setupHintDismissed: Boolean = false,
)
