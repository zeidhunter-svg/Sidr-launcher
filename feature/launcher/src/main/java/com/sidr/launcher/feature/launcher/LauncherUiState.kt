package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.suggestions.Suggestion

data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
)
