package com.sidr.launcher.data.repository.db

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Privacy guard for Room column **and table** names (Block F step F8.4; table-name scan added
 * Block H step H-a / Fork 3).
 *
 * Mirrors the DataStore key guard in [com.sidr.launcher.data.repository.preferences.PrivacyInventoryGuardTest]:
 * asserts that no persisted identifier string contains a forbidden term. Block F checked column
 * names only and exempted table names as "structural identifiers"; Block H removes that exemption —
 * a table named e.g. `voice_history` would be just as much a privacy-leak signal as a column, and
 * the absence-of-a-carrier guarantee should cover the whole schema surface.
 *
 * Forbidden categories (per architecture.md + Fork 1): conversation/AI history, location, raw
 * voice, free-text queries/searches, and secrets/keys/tokens. "history" is in the denylist because
 * no column or table should carry that semantic.
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

    /** Returns every (name, forbidden-term) violation found in [names]. */
    private fun scan(names: Set<String>): List<Pair<String, String>> =
        names.flatMap { name ->
            forbiddenTerms
                .filter { term -> name.contains(term, ignoreCase = true) }
                .map { term -> name to term }
        }

    @Test
    fun `no Room column name contains a forbidden term`() {
        val violations = scan(RoomColumnNames.ALL)
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (col, term) ->
                "  column \"$col\" contains forbidden term \"$term\""
            }
            fail("Forbidden field(s) carried by a Room column name:\n$detail")
        }
    }

    @Test
    fun `no Room table name contains a forbidden term`() {
        val violations = scan(RoomColumnNames.TABLE_NAMES)
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (table, term) ->
                "  table \"$table\" contains forbidden term \"$term\""
            }
            fail("Forbidden term carried by a Room table name:\n$detail")
        }
    }

    @Test
    fun `column set is non-empty`() {
        assertTrue("Expected Room columns to be inventoried", RoomColumnNames.ALL.isNotEmpty())
    }

    @Test
    fun `table set is non-empty`() {
        assertTrue("Expected Room tables to be inventoried", RoomColumnNames.TABLE_NAMES.isNotEmpty())
    }

    /**
     * Self-check that the scan has teeth: a deliberately-bad table name must be flagged. Guards
     * against the guard silently passing everything (e.g. an empty denylist or a broken predicate).
     */
    @Test
    fun `deliberately-bad table name is caught by the scan`() {
        val violations = scan(setOf("voice_history"))
        assertTrue(
            "Expected a bad table name to be flagged, but the scan found nothing",
            violations.isNotEmpty(),
        )
    }
}
