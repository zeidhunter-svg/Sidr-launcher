package com.sidr.launcher.data.repository.db

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Privacy guard for Room column names (Block F, step F8.4 / Fork 3).
 *
 * Mirrors the DataStore key guard in [com.sidr.launcher.data.repository.preferences.PrivacyInventoryGuardTest]:
 * asserts that no column name string contains a forbidden term. Column names are checked, NOT
 * table names — "intent_match" / "app_usage" are structural identifiers, not data carriers.
 *
 * Forbidden categories (per architecture.md + Fork 1): conversation/AI history, location, raw
 * voice, free-text queries/searches, and secrets/keys/tokens. "history" is in the denylist because
 * no column should carry that semantic; table names are exempt (structural, not data-carrying).
 */
class RoomColumnNamesGuardTest {

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
    fun `no Room column name contains a forbidden term`() {
        val violations = RoomColumnNames.ALL.flatMap { columnName ->
            forbiddenTerms
                .filter { term -> columnName.contains(term, ignoreCase = true) }
                .map { term -> columnName to term }
        }

        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (col, term) ->
                "  column \"$col\" contains forbidden term \"$term\""
            }
            fail("Forbidden field(s) carried by a Room column name:\n$detail")
        }
    }

    @Test
    fun `column set is non-empty`() {
        assertTrue("Expected Room columns to be inventoried", RoomColumnNames.ALL.isNotEmpty())
    }
}
