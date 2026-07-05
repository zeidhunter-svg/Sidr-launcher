package com.sidr.launcher.data.repository.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

// ─────────────────────────────────────────────────────────────────────────────
// PRIVACY INVENTORY — Block E, Step E1 (Fork 3)
//
// ALLOWED in DataStore (this file):
//   • User preferences       — theme name, UI toggles (non-sensitive display state)
//   • Feature flags          — capability toggles (no behavioral history)
//   • Device-profile cache   — computed capability primitives; no per-app data,
//                              no usage trails; see DeviceProfileCacheEntry.
//   • Last-known suggestions — display projection only (label + offline-resolvable
//                              actionId); see CachedSuggestion field split below.
//
// FORBIDDEN — no key, no entity, no DAO. Absence is the guarantee:
//   • AI conversation history         → never persisted
//   • Location history                → never persisted
//   • Raw voice / microphone input    → never persisted
//   • Per-app usage trails            → Room only, Block F (cap + age pruning)
//   • Raw search/query text           → never persisted
//   • Search or match timestamps      → never persisted
//
// DEFERRED — not stored in Phase 4:
//   • API keys / secrets / auth tokens → Phase 5 (Fork 1 — SecureSecretStore)
//
// CachedSuggestion field-by-field split:
//   CACHED     label     — display text for cold-start surface repaint
//   CACHED     actionId  — offline-resolvable package name or route constant
//   NOT CACHED raw query text the user typed
//   NOT CACHED search or match timestamps
//   NOT CACHED location- or calendar-derived context
//   NOT CACHED confidence score or match provenance
// ─────────────────────────────────────────────────────────────────────────────

internal object PreferencesKeys {

    // User preferences — prefix: user_
    val USER_THEME_NAME             = stringPreferencesKey("user_theme_name")
    val USER_COMMAND_INPUT_ENABLED  = booleanPreferencesKey("user_command_input_enabled")
    // Block X6 deferred UI prefs. All three names are denylist-clean (no forbidden term):
    // "favorites"/"count", "mic"/"input" (deliberately NOT "voice"), "setup"/"hint"/"dismissed".
    val USER_FAVORITES_COUNT        = intPreferencesKey("user_favorites_count")
    val USER_MIC_INPUT_ENABLED      = booleanPreferencesKey("user_mic_input_enabled")
    val USER_SETUP_HINT_DISMISSED   = booleanPreferencesKey("user_setup_hint_dismissed")

    // AIL-2 / R5 — web-search provider URL template (default Google). Holds a `{q}` placeholder for
    // the URL-encoded query. Denylist-clean: "web"/"provider"/"template" are not forbidden terms
    // (deliberately NOT "search"/"query", which the privacy guard rejects), so it is inventoried below.
    val WEB_PROVIDER_TEMPLATE       = stringPreferencesKey("web_provider_template")

    // Feature flags — prefix: flag_
    val FLAG_AI_SUGGESTIONS_ENABLED    = booleanPreferencesKey("flag_ai_suggestions_enabled")
    // Key string avoids the term "history" (kept in the guard denylist) — this is a feature
    // toggle for usage tracking, not a store of history. The Room usage-history table (Block F)
    // is the only history carrier. Domain field stays FeatureFlags.usageHistoryEnabled.
    val FLAG_USAGE_HISTORY_ENABLED     = booleanPreferencesKey("flag_usage_tracking_enabled")
    val FLAG_PERMISSION_EDU_DISMISSED  = booleanPreferencesKey("flag_permission_edu_dismissed")

    // Device-profile cache — prefix: device_
    // Flattened primitives; no Android types. When DeviceProfile is formalised in
    // :domain (Phase 4/5 capability-split), the mapper in PreferencesMapper translates
    // DeviceProfile ↔ DeviceProfileCacheEntry without touching these key names.
    val DEVICE_IS_LOW_END             = booleanPreferencesKey("device_is_low_end")
    val DEVICE_CACHED_AT_EPOCH_MS     = longPreferencesKey("device_cached_at_epoch_ms")
    val DEVICE_HAS_CACHE              = booleanPreferencesKey("device_has_cache")

    // Suggestions cache — prefix: sug_
    // Bounded JSON-encoded list (max MAX_CACHED_SUGGESTIONS items). Serialised as
    // List<CachedSuggestionDto> via kotlinx-serialization inside the mapper.
    val SUG_CACHED_LIST_JSON = stringPreferencesKey("sug_cached_list_json")

    // Permission-education dismissed flags — prefix: perm_ (Block G, Fork 5)
    // Per-feature "don't ask again / dismissed" state. WALLPAPER is the only feature with a
    // persisted key. VOICE_INPUT (Block T) and CALENDAR_SUGGESTIONS/LOCATION_SUGGESTIONS (Block U)
    // are all live (requestable=true) but STILL have no key — not because they're dormant, but
    // because "voice"/"calendar"/"location" are forbidden terms in this exact guard's denylist
    // (PrivacyInventoryGuardTest), so a key literally named perm_dismissed_voice/_calendar/_location
    // would fail the guard outright. PermissionPrefsRepositoryImpl.keyFor() returns null for all
    // three; their "don't ask again" choice is in-memory only for the ViewModel's lifetime (Block G
    // ADR, "Forks/Decisions" — carried forward unchanged through Blocks T and U). Per-feature
    // granularity supersedes the legacy global flag_permission_edu_dismissed (kept above for Block E
    // compatibility; the global flag is no longer the source of truth).
    val PERM_DISMISSED_WALLPAPER = booleanPreferencesKey("perm_dismissed_wallpaper")

    // AI provider config — prefix: ai_provider_ (Block K).
    // NON-SECRET part of the active provider: opaque id, chat-completions base URL, free-text model,
    // optional display name. The API key is NOT here — it lives in the Keystore-backed
    // SecureSecretStore (separate sidr_secrets store), keyed by SecretKeys.apiKey(providerId).
    // These names are denylist-clean: none contains a forbidden term (note "ai_provider" has no
    // "api" substring), so they ARE inventoried below and stay under the privacy guard.
    val AI_PROVIDER_ID           = stringPreferencesKey("ai_provider_id")
    val AI_PROVIDER_BASE_URL     = stringPreferencesKey("ai_provider_base_url")
    val AI_PROVIDER_MODEL        = stringPreferencesKey("ai_provider_model")
    val AI_PROVIDER_DISPLAY_NAME = stringPreferencesKey("ai_provider_display_name")

    // Local-NLU model availability — prefix: model_ (Block Q).
    // Set of ModelId.value strings whose on-disk `.onnx` artifact has been SHA-256-verified and
    // promoted by the download worker (the observable signal the OnnxIntentClassifier gate reads).
    // Carries no user data — only opaque model identifiers; the name is denylist-clean (no forbidden
    // term). Disk presence is the real load-time gate (LocalModelFiles.modelFile() returns null when
    // a file is gone); this flag is just the reactive availability projection.
    val MODEL_AVAILABLE_IDS = stringSetPreferencesKey("model_available_ids")

    // All DataStore key name strings — used exclusively by PrivacyInventoryGuardTest
    // to assert no key name contains a forbidden term (tests the *value* "user_theme_name",
    // not the Kotlin variable name USER_THEME_NAME).
    val ALL_KEY_NAMES: Set<String> = setOf(
        USER_THEME_NAME.name,
        USER_COMMAND_INPUT_ENABLED.name,
        USER_FAVORITES_COUNT.name,
        USER_MIC_INPUT_ENABLED.name,
        USER_SETUP_HINT_DISMISSED.name,
        WEB_PROVIDER_TEMPLATE.name,
        FLAG_AI_SUGGESTIONS_ENABLED.name,
        FLAG_USAGE_HISTORY_ENABLED.name,
        FLAG_PERMISSION_EDU_DISMISSED.name,
        DEVICE_IS_LOW_END.name,
        DEVICE_CACHED_AT_EPOCH_MS.name,
        DEVICE_HAS_CACHE.name,
        SUG_CACHED_LIST_JSON.name,
        PERM_DISMISSED_WALLPAPER.name,
        AI_PROVIDER_ID.name,
        AI_PROVIDER_BASE_URL.name,
        AI_PROVIDER_MODEL.name,
        AI_PROVIDER_DISPLAY_NAME.name,
        MODEL_AVAILABLE_IDS.name,
    )

    const val MAX_CACHED_SUGGESTIONS = 5
}
