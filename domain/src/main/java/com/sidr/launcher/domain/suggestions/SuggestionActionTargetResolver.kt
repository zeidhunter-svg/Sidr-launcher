package com.sidr.launcher.domain.suggestions

/**
 * Resolves suggestion targets into actions that the current device can actually open.
 *
 * The domain owns the small contract; Android-specific resolution through PackageManager/Intent lives
 * in the data layer. Providers may resolve coarse anchors (for example "alarms" or "camera") without
 * hardcoding OEM-specific package names, and the engine can validate package/route actionIds before
 * publishing or caching them.
 */
interface SuggestionActionTargetResolver {
    /** Resolve a universal anchor to a launchable package on this device, or null when absent. */
    suspend fun resolve(anchor: SuggestionActionAnchor): ResolvedSuggestionAction?

    /** True when [actionId] is a launchable package or an explicitly supported non-app action. */
    suspend fun isSupportedAction(actionId: String): Boolean
}

enum class SuggestionActionAnchor {
    ALARMS,
    CAMERA,
}

data class ResolvedSuggestionAction(
    val label: String,
    val actionId: String,
)
