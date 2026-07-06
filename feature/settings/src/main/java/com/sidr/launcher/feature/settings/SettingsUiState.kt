package com.sidr.launcher.feature.settings

/**
 * Immutable render state for the launcher settings screen (Block X5).
 *
 * [themeName] is the persisted `system | light | dark` choice (mirrors
 * `UserPreferences.themeName`); [aiSuggestionsEnabled] mirrors `FeatureFlags.aiSuggestionsEnabled`.
 * [usageHistoryEnabled] mirrors `FeatureFlags.usageHistoryEnabled` — the opt-in that lets the launcher
 * record app launches so the home **Favorites** row (and usage-based suggestion ranking) can populate;
 * off by default (privacy-first), so without it the Favorites row stays empty.
 * [favoritesCount] + [micInputEnabled] mirror the Block X6 `UserPreferences` deferred UI prefs.
 * [errorMessage] is a transient, display-safe write-failure string (never persisted).
 */
data class SettingsUiState(
    val aiSuggestionsEnabled: Boolean = false,
    val usageHistoryEnabled: Boolean = false,
    val themeName: String = ThemeOption.SYSTEM,
    /** Mirrors `UserPreferences.accentColor` — the brand phosphor (`green | amber`), AIL-6 / DF-7. */
    val accentColor: String = AccentOption.GREEN,
    val favoritesCount: Int = 8,
    val micInputEnabled: Boolean = true,
    /**
     * Mirrors `FeatureFlags.llmRouterEnabled` — the AIL-4 BYOK LLM Action Router. Off by default; when
     * on, natural-language commands the rules can't handle are routed by the configured cloud LLM
     * (proposals always confirm, never auto-execute). Needs a provider configured in Assistant setup.
     */
    val llmRouterEnabled: Boolean = false,
    val errorMessage: String? = null,
)

/** Selectable sizes for the home Favorites row (Block X6). */
val FAVORITES_COUNT_OPTIONS: List<Int> = listOf(4, 6, 8, 10)

/** The two brand accents (AIL-6 / DF-7). Values match `UserPreferences.accentColor`. */
object AccentOption {
    const val GREEN = "green"
    const val AMBER = "amber"

    /** Ordered options for the accent selector, each paired with its display label. */
    val ALL: List<Pair<String, String>> = listOf(
        GREEN to "Green",
        AMBER to "Amber",
    )
}

/** The three persisted theme choices. Values match `UserPreferences.themeName`. */
object ThemeOption {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    /** Ordered options for the settings selector, each paired with its display label. */
    val ALL: List<Pair<String, String>> = listOf(
        SYSTEM to "System default",
        LIGHT to "Light",
        DARK to "Dark",
    )
}
