package com.sidr.launcher.data.repository.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Privacy guard for Room column **and table** names (Block F step F8.4; table-name scan added
 * Block H step H-a / Fork 3; table-aware scoped exemption added Stage-2 S2-1 Task 9).
 *
 * Mirrors the DataStore key guard in [com.sidr.launcher.data.repository.preferences.PrivacyInventoryGuardTest]:
 * asserts that no persisted identifier string contains a forbidden term. Block F checked column
 * names only and exempted table names as "structural identifiers"; Block H removes that exemption —
 * a table named e.g. `voice_history` would be just as much a privacy-leak signal as a column, and
 * the absence-of-a-carrier guarantee should cover the whole schema surface.
 *
 * Task 9 makes the **column** scan table-aware: it scans fully-qualified `table.column` names and
 * skips a column only if that exact qualified name is in [RoomColumnNames.APPROVED_SENSITIVE_COLUMNS].
 * This is deliberately narrower than a bare column-name allow-list — `resolution_preferences.query`
 * is approved (normalized slot metadata only, never the raw user command; S2-1 spec §7), but an
 * unrelated table's same-named `query` column is still flagged. The table-name scan is unchanged
 * (no exemption applies there).
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

    /** Returns every (name, forbidden-term) violation found in [names]. Used for table names. */
    private fun scan(names: Set<String>): List<Pair<String, String>> =
        names.flatMap { name ->
            forbiddenTerms
                .filter { term -> name.contains(term, ignoreCase = true) }
                .map { term -> name to term }
        }

    /**
     * Table-aware column scan: builds the fully-qualified `table.column` name for every column in
     * [byTable] and flags it against [forbiddenTerms] UNLESS that exact qualified name is in
     * [approvedSensitiveColumns] — so the exemption is scoped to one table's one column, not a
     * bare column-name allow-list.
     */
    private fun scanColumns(
        byTable: Map<String, Set<String>>,
        approvedSensitiveColumns: Set<String> = RoomColumnNames.APPROVED_SENSITIVE_COLUMNS,
    ): List<Pair<String, String>> =
        byTable.flatMap { (table, columns) ->
            columns.flatMap { column ->
                val qualified = "$table.$column"
                if (qualified in approvedSensitiveColumns) {
                    emptyList()
                } else {
                    forbiddenTerms
                        .filter { term -> column.contains(term, ignoreCase = true) }
                        .map { term -> qualified to term }
                }
            }
        }

    @Test
    fun `no Room column name contains a forbidden term`() {
        val violations = scanColumns(RoomColumnNames.BY_TABLE)
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

    /**
     * Task 9 scoped-exemption teeth test, part (a): the approved column passes.
     * `resolution_preferences.query` is exempted because it carries normalized slot metadata, not
     * the raw user command (S2-1 spec §7) — the real, wired inventory must let it through.
     */
    @Test
    fun `approved resolution_preferences query column is allowed`() {
        val violations = scanColumns(RoomColumnNames.BY_TABLE)
        assertTrue(
            "Expected resolution_preferences.query to be exempted, but it was flagged: $violations",
            violations.none { (qualified, _) -> qualified == "resolution_preferences.query" },
        )
    }

    @Test
    fun `resolution preference local-sensitive column inventory is pinned`() {
        assertEquals(
            setOf("resolution_preferences.query"),
            RoomColumnNames.APPROVED_SENSITIVE_COLUMNS,
        )
        assertTrue(RoomColumnNames.TABLE_NAMES.contains("resolution_preferences"))
        assertTrue(RoomColumnNames.BY_TABLE["resolution_preferences"]?.contains("query") == true)
    }

    @Test
    fun `alias local-sensitive table and columns are inventoried`() {
        assertTrue(RoomColumnNames.TABLE_NAMES.contains("aliases"))
        assertEquals(
            setOf("phrase", "target_type", "target_package", "created_at"),
            RoomColumnNames.BY_TABLE["aliases"],
        )
        assertTrue(RoomColumnNames.ALL.containsAll(RoomColumnNames.ALIASES))
    }

    /**
     * Task 9 scoped-exemption teeth test, part (b): the exemption is table+column scoped, NOT a
     * bare "query" allow. An unrelated table's `query` column must still be flagged even though
     * the column name is identical to the approved one.
     */
    @Test
    fun `unapproved table's query column is still flagged`() {
        val hypotheticalByTable = mapOf("other_table" to setOf("query"))
        val violations = scanColumns(hypotheticalByTable)
        assertTrue(
            "Expected other_table.query to be flagged (exemption must be table+column scoped), " +
                "but the scan found nothing",
            violations.isNotEmpty(),
        )
        assertTrue(
            violations.any { (qualified, term) -> qualified == "other_table.query" && term == "query" },
        )
    }
}
