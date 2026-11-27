package com.sidr.launcher.domain.preferences

/**
 * How the user's three edits of Home's favourites change [UserPreferences] (A4′ acceptance tail, task 12; owner
 * ruling 15). Pure: the ViewModel reads the preferences, applies one of these and writes the result back through
 * the repository — the UI holds no rule.
 */
object FavoriteEdits {

    /** Pin [packageName] after the apps already pinned; a hidden app is unhidden. Already pinned — unchanged. */
    fun pin(prefs: UserPreferences, packageName: String): UserPreferences {
        if (packageName in prefs.pinnedFavorites) return prefs
        return prefs.copy(
            pinnedFavorites = prefs.pinnedFavorites + packageName,
            hiddenFavorites = prefs.hiddenFavorites - packageName,
        )
    }

    /** Take [packageName] off the pinned list; it competes for an automatic place again like any other app. */
    fun unpin(prefs: UserPreferences, packageName: String): UserPreferences {
        if (packageName !in prefs.pinnedFavorites) return prefs
        return prefs.copy(pinnedFavorites = prefs.pinnedFavorites - packageName)
    }

    /** Remove [packageName] from Home's automatic places (and unpin it). Its usage history stays. */
    fun hide(prefs: UserPreferences, packageName: String): UserPreferences =
        prefs.copy(
            pinnedFavorites = prefs.pinnedFavorites - packageName,
            hiddenFavorites = prefs.hiddenFavorites + packageName,
        )
}
