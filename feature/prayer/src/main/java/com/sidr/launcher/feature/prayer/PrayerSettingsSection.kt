package com.sidr.launcher.feature.prayer

import com.sidr.launcher.core.common.navigation.Routes

/**
 * One addressable section of the prayer setup page.
 *
 * [PrayerSettingsScreen] renders the whole page (all three sections, in this order) when no section
 * is requested — that is the Settings entry point and the first-run setup flow, both of which need
 * every choice visible at once. When [PrayerDetailScreen]'s Method / Madhab / Location rows push a
 * section, the screen renders ONLY that one, so each row lands somewhere visibly different.
 *
 * Crossing the nav boundary as [Enum.name] (never an ordinal), the Block-T
 * `PermissionFeature`/[Routes.PermissionEducation] precedent: `core/common`'s [Routes] stays a plain
 * string table and never depends on a feature module.
 */
enum class PrayerSettingsSection {
    METHOD,
    MADHAB,
    LOCATION,
    ;

    /** The concrete nav route that opens the setup page scoped to this section. */
    fun route(): String = Routes.PrayerSettings.routeFor(name)

    companion object {
        /**
         * Parses a nav-arg string back into a section. An absent OR unrecognised value degrades to
         * `null` (⇒ the whole setup page) rather than throwing — a malformed deep link must never
         * crash the screen, and the full page is always a truthful answer.
         */
        fun fromNavArg(raw: String?): PrayerSettingsSection? =
            entries.firstOrNull { it.name == raw }
    }
}
