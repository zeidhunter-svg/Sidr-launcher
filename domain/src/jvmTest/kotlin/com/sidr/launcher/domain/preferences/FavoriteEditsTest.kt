package com.sidr.launcher.domain.preferences

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame

/**
 * A4′ acceptance tail, task 12 (owner ruling 15): how pin / unpin / hide edit the two favourites lists. Pure — the
 * ViewModel reads the preferences, applies one of these and writes the result back.
 */
class FavoriteEditsTest {

    @Test
    fun `the count defaults to 12 and nothing is pinned or hidden`() {
        val prefs = UserPreferences()
        assertEquals(12, prefs.favoritesCount)
        assertEquals(emptyList<String>(), prefs.pinnedFavorites)
        assertEquals(emptySet<String>(), prefs.hiddenFavorites)
    }

    @Test
    fun `pinning appends in the order of pinning`() {
        val prefs = FavoriteEdits.pin(FavoriteEdits.pin(UserPreferences(), "com.b"), "com.a")
        assertEquals(listOf("com.b", "com.a"), prefs.pinnedFavorites)
    }

    @Test
    fun `pinning an already pinned app changes nothing`() {
        val prefs = UserPreferences(pinnedFavorites = listOf("com.b", "com.a"))
        assertSame(prefs, FavoriteEdits.pin(prefs, "com.b"))
    }

    @Test
    fun `pinning a hidden app unhides it`() {
        val prefs = FavoriteEdits.pin(UserPreferences(hiddenFavorites = setOf("com.a", "com.c")), "com.a")
        assertEquals(listOf("com.a"), prefs.pinnedFavorites)
        assertEquals(setOf("com.c"), prefs.hiddenFavorites)
    }

    @Test
    fun `unpinning removes the app and keeps the others in order`() {
        val prefs = FavoriteEdits.unpin(UserPreferences(pinnedFavorites = listOf("com.a", "com.b", "com.c")), "com.b")
        assertEquals(listOf("com.a", "com.c"), prefs.pinnedFavorites)
    }

    @Test
    fun `hiding adds the app to the hidden set and unpins it`() {
        val prefs = FavoriteEdits.hide(UserPreferences(pinnedFavorites = listOf("com.a", "com.b")), "com.a")
        assertEquals(setOf("com.a"), prefs.hiddenFavorites)
        assertEquals(listOf("com.b"), prefs.pinnedFavorites)
    }
}
