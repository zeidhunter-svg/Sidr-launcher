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
    /** Mirrors `UserPreferences.accentColor` — the brand accent (`grey | green | amber`), AIL-6 / DF-7. */
    val accentColor: String = AccentOption.GREY,
    val favoritesCount: Int = 8,
    val micInputEnabled: Boolean = true,
    /** Mirrors `UserPreferences.autoHideNavBar` — when on, the bottom navigation recedes after idle
     *  and is summoned back via a handle. **Off by default** since DS-11 (2026-08-10): pinned chrome
     *  is the resting state and auto-hide is the opt-in. */
    val autoHideNavBar: Boolean = false,
    /**
     * Mirrors `FeatureFlags.localOnlyMode` (Этап 4.0, ADR 1/4). **Off by default** — understanding
     * belongs to the model, so a command FastPath can't handle is routed to the configured provider
     * (proposals always confirm, never auto-execute). On, nothing understanding-related leaves the
     * device and routing is byte-for-byte the FastPath-only launcher.
     *
     * Replaces the inverted `llmRouterEnabled`, which made understanding itself opt-in.
     */
    val localOnlyMode: Boolean = false,
    val errorMessage: String? = null,
)

/** Selectable sizes for the home Favorites row (Block X6). */
val FAVORITES_COUNT_OPTIONS: List<Int> = listOf(4, 6, 8, 10)

/** The three brand accents (AIL-6 / DF-7; grey reactivated as default 2026-07-12). Values match
 *  `UserPreferences.accentColor`. */
object AccentOption {
    const val GREY = "grey"
    const val GREEN = "green"
    const val AMBER = "amber"

    /** Ordered options for the accent selector, each paired with its display label. */
    val ALL: List<Pair<String, String>> = listOf(
        GREY to "Grey",
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
