package com.sidr.launcher.domain.preferences

data class UserPreferences(
    val themeName: String = "system",          // "system" | "light" | "dark"
    val commandInputEnabled: Boolean = true,
)
