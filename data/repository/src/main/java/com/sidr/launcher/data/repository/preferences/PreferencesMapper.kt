package com.sidr.launcher.data.repository.preferences

import androidx.datastore.preferences.core.Preferences
import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.preferences.DeviceProfileCacheEntry
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.preferences.UserPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure Preferences ↔ domain mapping. No Android framework, no Hilt — just the DataStore
 * [Preferences] snapshot type and domain models. Keeps DataStore serialisation concerns
 * (keys, JSON encoding, defaults) inside :data:repository, off the domain boundary.
 *
 * Reads tolerate missing keys by falling back to the domain model's own defaults, so a
 * fresh install / cleared store observes well-formed defaults rather than nulls.
 */
internal object PreferencesMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── User preferences ──────────────────────────────────────────────────────
    fun toUserPreferences(prefs: Preferences): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themeName = prefs[PreferencesKeys.USER_THEME_NAME] ?: defaults.themeName,
            commandInputEnabled = prefs[PreferencesKeys.USER_COMMAND_INPUT_ENABLED]
                ?: defaults.commandInputEnabled,
        )
    }

    fun writeUserPreferences(prefs: androidx.datastore.preferences.core.MutablePreferences, value: UserPreferences) {
        prefs[PreferencesKeys.USER_THEME_NAME] = value.themeName
        prefs[PreferencesKeys.USER_COMMAND_INPUT_ENABLED] = value.commandInputEnabled
    }

    // ── Feature flags ─────────────────────────────────────────────────────────
    fun toFeatureFlags(prefs: Preferences): FeatureFlags {
        val defaults = FeatureFlags()
        return FeatureFlags(
            aiSuggestionsEnabled = prefs[PreferencesKeys.FLAG_AI_SUGGESTIONS_ENABLED]
                ?: defaults.aiSuggestionsEnabled,
            usageHistoryEnabled = prefs[PreferencesKeys.FLAG_USAGE_HISTORY_ENABLED]
                ?: defaults.usageHistoryEnabled,
            permissionEducationDismissed = prefs[PreferencesKeys.FLAG_PERMISSION_EDU_DISMISSED]
                ?: defaults.permissionEducationDismissed,
        )
    }

    fun writeFeatureFlags(prefs: androidx.datastore.preferences.core.MutablePreferences, value: FeatureFlags) {
        prefs[PreferencesKeys.FLAG_AI_SUGGESTIONS_ENABLED] = value.aiSuggestionsEnabled
        prefs[PreferencesKeys.FLAG_USAGE_HISTORY_ENABLED] = value.usageHistoryEnabled
        prefs[PreferencesKeys.FLAG_PERMISSION_EDU_DISMISSED] = value.permissionEducationDismissed
    }

    // ── Device-profile cache (nullable: absent until first write) ──────────────
    fun toDeviceProfileCacheEntry(prefs: Preferences): DeviceProfileCacheEntry? {
        val hasCache = prefs[PreferencesKeys.DEVICE_HAS_CACHE] ?: false
        if (!hasCache) return null
        val defaults = DeviceProfileCacheEntry()
        return DeviceProfileCacheEntry(
            isLowEndDevice = prefs[PreferencesKeys.DEVICE_IS_LOW_END] ?: defaults.isLowEndDevice,
            cachedAtEpochMs = prefs[PreferencesKeys.DEVICE_CACHED_AT_EPOCH_MS] ?: defaults.cachedAtEpochMs,
        )
    }

    fun writeDeviceProfileCacheEntry(prefs: androidx.datastore.preferences.core.MutablePreferences, value: DeviceProfileCacheEntry) {
        prefs[PreferencesKeys.DEVICE_HAS_CACHE] = true
        prefs[PreferencesKeys.DEVICE_IS_LOW_END] = value.isLowEndDevice
        prefs[PreferencesKeys.DEVICE_CACHED_AT_EPOCH_MS] = value.cachedAtEpochMs
    }

    fun clearDeviceProfileCache(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(PreferencesKeys.DEVICE_HAS_CACHE)
        prefs.remove(PreferencesKeys.DEVICE_IS_LOW_END)
        prefs.remove(PreferencesKeys.DEVICE_CACHED_AT_EPOCH_MS)
    }

    // ── Suggestions cache (bounded JSON list) ──────────────────────────────────
    fun toCachedSuggestions(prefs: Preferences): List<CachedSuggestion> {
        val raw = prefs[PreferencesKeys.SUG_CACHED_LIST_JSON] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<CachedSuggestionDto>>(raw).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    fun writeCachedSuggestions(prefs: androidx.datastore.preferences.core.MutablePreferences, value: List<CachedSuggestion>) {
        val bounded = value.take(PreferencesKeys.MAX_CACHED_SUGGESTIONS).map { it.toDto() }
        prefs[PreferencesKeys.SUG_CACHED_LIST_JSON] = json.encodeToString(bounded)
    }

    /**
     * Serialisation carrier for [CachedSuggestion]. Lives here, not in :domain, so the
     * domain model stays annotation-free (kotlinx-serialization is a data-layer concern).
     */
    @Serializable
    private data class CachedSuggestionDto(
        val label: String,
        val actionId: String,
    )

    private fun CachedSuggestion.toDto() = CachedSuggestionDto(label = label, actionId = actionId)
    private fun CachedSuggestionDto.toDomain() = CachedSuggestion(label = label, actionId = actionId)
}
