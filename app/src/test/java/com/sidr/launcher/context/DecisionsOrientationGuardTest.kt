package com.sidr.launcher.context

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Context hygiene, 2026-11-21 (owner decision «5: a», with forks «3: a+a′ · 5: a» of that session). Holds the
 * orientation file `ai-context/decisions-orientation.md` beside the ADR journal `ai-context/decisions.md`.
 *
 * **What problem this solves.** The journal is 1.1 MB and is an archive by address: it is never cut, so every
 * «ADR «…»» citation in code, KDoc and documents keeps resolving by its heading. Nobody reads it whole. The
 * orientation file is the part that IS read whole: one line per journal heading, marked in force / replaced /
 * history, plus a short block of what binds the next session. Two things make such a file rot, and this guard
 * holds both:
 * 1. **Growth** — the same decay `CLAUDE.md` showed between Этап 0.4 and 2026-11-11 ([ClaudeMdSizeGuardTest]).
 *    Ceilings on lines (the owner's measure: «~150 lines, read whole») and on bytes (long lines defeat a line
 *    count).
 * 2. **Omission** — a closing session appends its ADR to the journal and forgets the orientation line. Every
 *    `## ` heading of the journal, outside fenced code, must stand in the orientation file **verbatim**.
 *
 * **What it does not hold**, said: whether the in-force / replaced marks are true (a judgement, the closing
 * session's), and whether «ADR «…»» citations elsewhere resolve (unchanged by this session because the journal
 * is not cut; seven citations were loose already on 2026-11-21 — placeholders and fragments — named in the ADR).
 *
 * **What a red guard means.** Size: compress lines, do not raise the ceiling (an owner decision). Omission: add
 * the heading's line, and re-mark the lines of the ADRs it replaced.
 *
 * **Declared inputs.** `app/build.gradle.kts` declares both files as inputs of every `Test` task.
 *
 * Working directory is the module dir (`app`), so the repo root is `..`.
 */
class DecisionsOrientationGuardTest {

    private val repoRoot = File("..")
    private val orientation = File(repoRoot, "ai-context/decisions-orientation.md")
    private val journal = File(repoRoot, "ai-context/decisions.md")

    @Test
    fun `orientation file and journal exist`() {
        assertTrue(
            "ai-context/decisions-orientation.md not found at ${orientation.absolutePath}; an absent file " +
                "must not pass as a small or complete one.",
            orientation.isFile,
        )
        assertTrue("ai-context/decisions.md not found at ${journal.absolutePath}.", journal.isFile)
    }

    @Test
    fun `orientation file stays under its line ceiling`() {
        val lines = orientation.readLines().size
        assertTrue(
            "decisions-orientation.md has $lines lines, over the ceiling of $CEILING_LINES. It is read whole; " +
                "compress lines (group history entries), do not raise the ceiling — that is an owner decision.",
            lines <= CEILING_LINES,
        )
    }

    @Test
    fun `orientation file stays under its byte ceiling`() {
        val bytes = orientation.length()
        assertTrue(
            "decisions-orientation.md is $bytes bytes, over the ceiling of $CEILING_BYTES. Shorten notes, keep " +
                "headings verbatim; raising the ceiling is an owner decision.",
            bytes <= CEILING_BYTES,
        )
    }

    @Test
    fun `every journal heading stands in the orientation file verbatim`() {
        val headings = journalHeadings()
        assertTrue("no `## ` heading parsed from decisions.md — the parse is broken", headings.size >= MIN_HEADINGS)
        val text = orientation.readText()
        val missing = headings.filterNot { text.contains(it) }
        assertTrue(
            "decisions-orientation.md lacks ${missing.size} journal heading(s); add one line per heading, " +
                "verbatim, with its mark (в силе / заменён → … / история):\n" + missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    /** `## ` headings of the journal, skipping fenced code blocks (a quoted heading is not a heading). */
    private fun journalHeadings(): List<String> {
        var fenced = false
        val out = mutableListOf<String>()
        for (line in journal.readLines()) {
            if (line.trimStart().startsWith("```")) {
                fenced = !fenced
                continue
            }
            if (!fenced && line.startsWith("## ")) out += line.removePrefix("## ").trim()
        }
        return out
    }

    private companion object {
        /**
         * 200 lines / 24 000 bytes: the file landed at 167 lines and ~19.4 KB with 73 journal headings, so the
         * ceilings leave room for roughly fifteen more ADR lines — several block closes — before the next
         * compression is due.
         */
        const val CEILING_LINES = 200
        const val CEILING_BYTES = 24_000L

        /** The journal had 73 `## ` headings on 2026-11-21; a parse yielding fewer than this is broken. */
        const val MIN_HEADINGS = 70
    }
}
