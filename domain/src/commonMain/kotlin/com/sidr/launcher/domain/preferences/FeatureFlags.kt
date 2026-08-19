package com.sidr.launcher.domain.preferences

data class FeatureFlags(
    val aiSuggestionsEnabled: Boolean = false,
    val usageHistoryEnabled: Boolean = false,
    val permissionEducationDismissed: Boolean = false,
    /**
     * Explicit "understanding never leaves this device" opt-out (Этап 4.0, ADR 1/4). **Off by
     * default** — understanding belongs to the model, so a FastPath miss reaches the planner whenever
     * a provider is configured and the device is online. On, the planner is never consulted and
     * routing is byte-for-byte the FastPath-only launcher.
     *
     * Replaces the inverted `llmRouterEnabled` (key `flag_llm_router_enabled`), which gated
     * understanding itself. It lives on a **new** DataStore key (`flag_local_only`) rather than
     * flipping the old default in place: `PreferencesMapper.writeFeatureFlags` persists the whole
     * object on every settings change, so a stored `false` would beat a changed default for exactly
     * the users who have used the app — the DS-11 `alwaysShowNavBar` → `autoHideNavBar` precedent,
     * which was caught on device rather than in review. The old key is orphaned with no migration.
     */
    val localOnlyMode: Boolean = false,
)
