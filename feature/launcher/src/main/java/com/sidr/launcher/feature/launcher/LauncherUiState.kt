package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.suggestions.Suggestion

data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    // Block X2: the small top-N most-used set rendered on the decluttered home. Derived from usage
    // history ∩ installed apps; the full [apps] list stays loaded (drawer + suggestion resolution).
    val favorites: List<InstalledApp> = emptyList(),
    // A4′ acceptance tail, task 12: the packages the user pinned, in pinning order — the picker's switches. Of
    // [favorites], the ones NOT in here were placed automatically (and are the ones that offer «Убрать с главного»).
    val pinnedPackages: List<String> = emptyList(),
    // Block X6: whether the user has dismissed (or acted on) the first-run "set as default launcher"
    // nudge. The nudge shows on home only when this is false AND the launcher is not already default
    // (the default-launcher check is a runtime Android query performed in the screen).
    val setupHintDismissed: Boolean = false,
)
