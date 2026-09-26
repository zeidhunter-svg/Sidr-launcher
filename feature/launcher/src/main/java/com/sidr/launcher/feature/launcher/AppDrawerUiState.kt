package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp

/**
 * State for the App Drawer surface (Block X3): every installed app, grouped alphabetically into
 * lettered [sections] for section headers + fast orientation. Ordering here is purely alphabetical —
 * the usage-aware ordering lives on the home Favorites row, not in the drawer.
 */
data class AppDrawerUiState(
    val sections: List<DrawerSection> = emptyList(),
)

/**
 * One alphabetical bucket of the drawer: a [letter] header (A–Z, or "#" for apps whose label does
 * not start with a letter) over the [apps] that fall under it, themselves alphabetically ordered.
 */
data class DrawerSection(
    val letter: String,
    val apps: List<InstalledApp>,
)

/** The bucket label for apps whose first character is not a letter (digits, symbols, emoji…). */
private const val NON_ALPHA_SECTION = "#"

/**
 * Pure live filter (Block X4): keep the [apps] whose [InstalledApp.label] contains [query]
 * case-insensitively. A blank / whitespace-only [query] means "no filter" → the full list is
 * returned unchanged. Substring (not prefix) matching, so "tele" still finds "Telegram" and a
 * mid-word fragment matches too.
 *
 * Side-effect-free and Android-free so it is trivially unit-testable and can run over the already
 * loaded installed-app list without touching the repository again.
 */
internal fun filterApps(apps: List<InstalledApp>, query: String): List<InstalledApp> {
    val needle = query.trim()
    if (needle.isEmpty()) return apps
    return apps.filter { it.label.contains(needle, ignoreCase = true) }
}

/**
 * Pure grouping: sort [apps] by label (case-insensitive, locale-stable) and split into sections
 * keyed by the uppercased first letter. Labels not starting with a letter collapse into a single
 * trailing [NON_ALPHA_SECTION] bucket. Empty input → empty list.
 *
 * Kept side-effect-free so it is trivially unit-testable and can run off the app-load result without
 * touching Android or coroutines.
 */
internal fun groupIntoSections(apps: List<InstalledApp>): List<DrawerSection> {
    if (apps.isEmpty()) return emptyList()

    val sorted = apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    // Preserve first-seen key order (already alphabetical after the sort), so letter sections come
    // out A→Z; the non-alpha bucket is appended last regardless of where its members sorted.
    val byLetter = LinkedHashMap<String, MutableList<InstalledApp>>()
    val nonAlpha = mutableListOf<InstalledApp>()
    for (app in sorted) {
        val first = app.label.firstOrNull()
        if (first != null && first.isLetter()) {
            val key = first.uppercaseChar().toString()
            byLetter.getOrPut(key) { mutableListOf() }.add(app)
        } else {
            nonAlpha.add(app)
        }
    }

    val sections = byLetter.map { (letter, entries) -> DrawerSection(letter, entries) }
    return if (nonAlpha.isEmpty()) sections else sections + DrawerSection(NON_ALPHA_SECTION, nonAlpha)
}
