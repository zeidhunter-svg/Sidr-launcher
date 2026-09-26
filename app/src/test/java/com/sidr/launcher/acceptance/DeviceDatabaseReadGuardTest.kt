package com.sidr.launcher.acceptance

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A1′ device-acceptance defect, 2026-09-10. Holds the one property that makes a device-acceptance
 * checklist *evidence* rather than decoration: **a command the owner copy-pastes to read the agent
 * tables must read the database, not a stale image of it.**
 *
 * **The incident this exists because of.** The A1′ checklist prescribed
 * `adb exec-out run-as com.sidr.launcher cat databases/sidr_history.db > /tmp/sidr.db`, then queried
 * the copy with the SDK's `sqlite3`. Room opens `sidr_history.db` in WAL mode (measured on the
 * SM-A325F: `PRAGMA journal_mode` → `wal`), so a committed write is durable in
 * `sidr_history.db-wal` and reaches the main file only at a **checkpoint**. Copying the main file
 * alone therefore yields the database *as of the last checkpoint* — which during the 2026-09-10 owner
 * run was three hours and four state transitions stale. It cost that run its Part B: a session the
 * engine had correctly deleted was read back as still present, and an `AwaitingConsent` session that
 * had been persisted in full was read back as `Running` with one trace event. Both "defects" were the
 * read.
 *
 * **Why a guard and not a note.** The truncated read fails in *both* directions. It reported a row
 * that was gone (false RED, what happened); it can equally report "all three `agent_*` tables are
 * empty" while a row holding `agent_session.goal_text` — the user's raw command — is on disk. That
 * second direction is a **false GREEN on a privacy guarantee**, and it is the direction nobody
 * notices. This repo has twice recorded a silently-lying command in prose — piping `gradlew` through
 * `tail` (2026-07-13) and the build-level `--rerun` flag — and the second one still produced a false
 * green *after* being written down. Prose did not hold. This does.
 *
 * **What it checks, at the granularity of the mistake.** The unit an owner copies is a fenced block,
 * so the rule is per block, not per file: a fenced block that reads `databases/sidr_history.db` must
 * also pull `databases/sidr_history.db-wal`. A block may instead invoke [PULL_SCRIPT], which does the
 * whole pull correctly in one command.
 *
 * **Named limitations**, in the style of the `:app` call-site guards:
 *  - it scans [PLANS_DIR] only, because that is where device-acceptance checklists live and both
 *    offenders were there. A checklist written under `docs/governing/` or `ai-context/` is invisible
 *    to it. Widening the scan means widening this test's `inputs.dir` declaration in
 *    `app/build.gradle.kts` **in the same commit** — the same obligation the two call-site guards
 *    carry, for the same reason.
 *  - it reads text, so it holds what a checklist *prescribes*, never what an owner actually typed.
 *  - `-shm` is deliberately not required: SQLite rebuilds the wal-index from the `-wal` when it is
 *    missing or stale, so the `.db` + `.db-wal` pair is sufficient (verified 2026-09-10 by reading a
 *    pulled pair both with and without the `-shm`).
 */
class DeviceDatabaseReadGuardTest {

    private val repoRoot = File("..")
    private val plansDir = File(repoRoot, PLANS_DIR)

    private data class Block(val file: String, val startLine: Int, val text: String)

    /** Every fenced code block in every checklist, with the file and line that opens it. */
    private val blocks: List<Block> by lazy {
        assertTrue(
            "Device-acceptance checklists not found at $PLANS_DIR. If they moved, this guard and its " +
                "`inputs.dir` declaration in app/build.gradle.kts move with them.",
            plansDir.isDirectory,
        )
        plansDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .flatMap { file -> fencedBlocks(file.name, file.readLines()) }
            .toList()
    }

    private fun fencedBlocks(name: String, lines: List<String>): List<Block> {
        val out = mutableListOf<Block>()
        var start = -1
        val buffer = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (line.trimStart().startsWith("```")) {
                if (start < 0) {
                    start = index + 1
                    buffer.setLength(0)
                } else {
                    out += Block(name, start, buffer.toString())
                    start = -1
                }
            } else if (start >= 0) {
                buffer.append(line).append('\n')
            }
        }
        return out
    }

    /**
     * The guard proper. A block that reads the Room database must pull the write-ahead log with it,
     * or delegate to the script that does.
     */
    @Test
    fun `a checklist block that reads the agent database also pulls the write-ahead log`() {
        val offenders = blocks
            .filter { DB_PATH in it.text }
            .filterNot { WAL_PATH in it.text || PULL_SCRIPT in it.text }
            .map { "${it.file}:${it.startLine}" }

        assertTrue(
            "These fenced blocks read $DB_PATH without pulling $WAL_PATH: $offenders.\n" +
                "Room runs this database in WAL mode, so the main file alone is the database as of " +
                "the last checkpoint — it can report a deleted session as present, and an existing " +
                "session (with the user's raw goal_text) as absent. Pull the pair, or call " +
                "$PULL_SCRIPT.",
            offenders.isEmpty(),
        )
    }

    /**
     * `walkTopDown()` over a directory that no longer holds what the guard is about asserts nothing at
     * all — the vacuous-guard class Этап 2 found in three privacy guards. So the corpus is asserted to
     * still contain the thing being guarded.
     */
    @Test
    fun `the scan is not vacuous - checklists exist and at least one reads the agent database`() {
        assertTrue("No fenced blocks scanned under $PLANS_DIR.", blocks.isNotEmpty())
        assertTrue(
            "No checklist block under $PLANS_DIR reads $DB_PATH any more. Either the database was " +
                "renamed (update DB_PATH/WAL_PATH here) or device acceptance no longer reads the " +
                "agent tables — in which case this guard is guarding nothing and should be deleted, " +
                "not left green.",
            blocks.any { DB_PATH in it.text },
        )
    }

    /**
     * The other way a checklist command produces no evidence — measured on the SM-A325F 2026-09-05:
     * `run-as: exec failed for sqlite3: No such file or directory`. Android 13 exposes no `sqlite3`
     * binary to this app's `run-as` context, so any block that asks the device to run one stops the
     * owner at the first item that reads the trace. Loud rather than silent, but still a checklist
     * that cannot produce what it asks for.
     */
    @Test
    fun `no checklist block runs sqlite3 on the device`() {
        val offenders = blocks
            .filter { block ->
                block.text.lineSequence().any { "run-as" in it && "sqlite3" in it }
            }
            .map { "${it.file}:${it.startLine}" }

        assertTrue(
            "These fenced blocks invoke sqlite3 through run-as: $offenders. The SM-A325F has no " +
                "sqlite3 reachable that way (measured 2026-09-05). Pull the files and query the copy " +
                "with the SDK's own sqlite3 at \$ANDROID_HOME/platform-tools/sqlite3.",
            offenders.isEmpty(),
        )
    }

    private companion object {
        const val PLANS_DIR = "docs/superpowers/plans"
        const val DB_PATH = "databases/sidr_history.db"
        const val WAL_PATH = "databases/sidr_history.db-wal"
        const val PULL_SCRIPT = "tools/device/pull-agent-db.sh"
    }
}
