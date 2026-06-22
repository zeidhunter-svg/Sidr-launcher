package com.sidr.launcher.domain.preferences

data class FeatureFlags(
    val aiSuggestionsEnabled: Boolean = false,
    val usageHistoryEnabled: Boolean = false,
    val permissionEducationDismissed: Boolean = false,
)
