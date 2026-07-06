package com.sidr.launcher.domain.preferences

data class FeatureFlags(
    val aiSuggestionsEnabled: Boolean = false,
    val usageHistoryEnabled: Boolean = false,
    val permissionEducationDismissed: Boolean = false,
    /**
     * AIL-4 BYOK LLM Action Router toggle. **Off by default** — with it off, command routing is
     * byte-for-byte the current rule-only launcher (`RouteCommandUseCase` returns the rule outcome
     * without ever consulting the planner). When on, the planner is still consulted **only** on a
     * low-confidence / natural-language command, and its proposals never auto-execute (Fork R4).
     */
    val llmRouterEnabled: Boolean = false,
)
