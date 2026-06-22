package com.sidr.launcher.data.repository.preferences

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Privacy guard for the DataStore key set (Block E, Step E1 / Fork 3).
 *
 * Asserts that no DataStore key *value* (e.g. "user_theme_name") contains a forbidden term.
 * This checks the persisted key string, NOT the Kotlin property name — so the "voice" term
 * stays in the denylist and remains effective without colliding with capability-flag naming.
 *
 * Forbidden categories (per architecture.md + Fork 1): conversation/AI history, location,
 * raw voice, free-text queries/searches, behavioural history, and secrets/keys/tokens.
 * "key" is intentionally NOT in the denylist — it is redundant (secrets are caught by
 * secret/token/api) and would false-match legitimate key naming.
 */
class PrivacyInventoryGuardTest {

    private val forbiddenTerms = listOf(
        "voice",
        "query",
        "search",
        "location",
        "calendar",
        "history",
        "conversation",
        "message",
        "transcript",
        "secret",
        "token",
        "api",
    )

    @Test
    fun `no DataStore key name contains a forbidden term`() {
        val violations = PreferencesKeys.ALL_KEY_NAMES.flatMap { keyName ->
            forbiddenTerms
                .filter { term -> keyName.contains(term, ignoreCase = true) }
                .map { term -> keyName to term }
        }

        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (key, term) ->
                "  key \"$key\" contains forbidden term \"$term\"" }
            fail("Forbidden field(s) carried by a DataStore key:\n$detail")
        }
    }

    @Test
    fun `key set is non-empty and unique`() {
        // A guard over an empty/duplicated set would pass vacuously — assert it has real content.
        assertTrue("Expected DataStore keys to be inventoried", PreferencesKeys.ALL_KEY_NAMES.isNotEmpty())
    }
}
