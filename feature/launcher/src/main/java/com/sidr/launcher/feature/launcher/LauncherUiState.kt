package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp

data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
)
