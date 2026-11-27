package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A4′ acceptance tail, task 12 (owner ruling 15): the Home favourites are the user's pinned apps, then the
 * most-used ones that are still fresh (opened within 14 days), minus the ones the user removed.
 */
class LauncherAppListFavoritesTest {

    private val now = 100L * DAY
    private val installed = ('a'..'h').map { InstalledApp("com.$it", "App $it") }

    // Records arrive most-used-first (the repository's contract); the default is fresh.
    private fun record(pkg: String, ageDays: Long = 0) =
        AppUsageRecord("com.$pkg", lastUsedEpochMs = now - ageDays * DAY, launchCount = 1)

    private fun derive(
        records: List<AppUsageRecord>,
        prefs: UserPreferences = UserPreferences(),
        apps: List<InstalledApp> = installed,
    ) = deriveFavorites(records, apps, prefs, now).map { it.packageName }

    @Test
    fun `pinned apps come first and are never pushed out`() {
        val records = listOf(record("a"), record("b"), record("c"))
        val prefs = UserPreferences(favoritesCount = 3, pinnedFavorites = listOf("com.h", "com.g"))
        assertEquals(listOf("com.h", "com.g", "com.a"), derive(records, prefs))
    }

    @Test
    fun `a pinned app is shown even with no usage history and is not repeated by its record`() {
        val records = listOf(record("a"), record("g"))
        val prefs = UserPreferences(pinnedFavorites = listOf("com.g"))
        assertEquals(listOf("com.g", "com.a"), derive(records, prefs))
    }

    @Test
    fun `an app not opened for 14 days leaves the automatic places`() {
        val records = listOf(record("a", ageDays = 15), record("b", ageDays = 14), record("c", ageDays = 13))
        assertEquals(listOf("com.b", "com.c"), derive(records))
    }

    @Test
    fun `a stale pinned app stays`() {
        val records = listOf(record("a", ageDays = 40))
        val prefs = UserPreferences(pinnedFavorites = listOf("com.a"))
        assertEquals(listOf("com.a"), derive(records, prefs))
    }

    @Test
    fun `a new app enters once it outranks the last fresh one`() {
        val prefs = UserPreferences(favoritesCount = 2)
        val before = listOf(record("a"), record("b"), record("c"))
        assertEquals(listOf("com.a", "com.b"), derive(before, prefs))
        // «d» has been opened more than «b» now: the repository puts it second, «b» leaves the row.
        val after = listOf(record("a"), record("d"), record("b"), record("c"))
        assertEquals(listOf("com.a", "com.d"), derive(after, prefs))
    }

    @Test
    fun `a hidden app is not shown and its history stays`() {
        val records = listOf(record("a"), record("b"), record("c"))
        val prefs = UserPreferences(hiddenFavorites = setOf("com.b"))
        assertEquals(listOf("com.a", "com.c"), derive(records, prefs))
        // The hidden set is all that excludes it — the records are untouched, so un-hiding brings it back.
        assertEquals(listOf("com.a", "com.b", "com.c"), derive(records, UserPreferences()))
    }

    @Test
    fun `pinning a hidden app unhides it`() {
        val hidden = UserPreferences(hiddenFavorites = setOf("com.b"))
        val pinned = com.sidr.launcher.domain.preferences.FavoriteEdits.pin(hidden, "com.b")
        assertEquals(listOf("com.b", "com.a"), derive(listOf(record("a")), pinned))
    }

    @Test
    fun `an uninstalled pinned app is skipped`() {
        val prefs = UserPreferences(pinnedFavorites = listOf("com.gone", "com.b"))
        assertEquals(listOf("com.b", "com.a"), derive(listOf(record("a")), prefs))
    }

    @Test
    fun `an empty history and nothing pinned gives no favourites`() {
        assertEquals(emptyList<String>(), derive(emptyList()))
    }

    @Test
    fun `everything is capped at the count`() {
        val records = (listOf("a", "b", "c", "d")).map { record(it) }
        val prefs = UserPreferences(favoritesCount = 3, pinnedFavorites = listOf("com.h", "com.g", "com.f", "com.e"))
        assertEquals(listOf("com.h", "com.g", "com.f"), derive(records, prefs))
    }

    @Test
    fun `a count of zero gives no favourites even with pinned apps`() {
        val prefs = UserPreferences(favoritesCount = 0, pinnedFavorites = listOf("com.a"))
        assertEquals(emptyList<String>(), derive(listOf(record("b")), prefs))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
