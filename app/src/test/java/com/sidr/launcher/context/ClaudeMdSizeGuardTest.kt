package com.sidr.launcher.context

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Context hygiene, 2026-11-11 (owner proposal 2 of that session). A ceiling on the size of `CLAUDE.md`.
 *
 * **What problem this solves.** `CLAUDE.md` is loaded into the context of every session and of every
 * subagent spawned from it, so its size is a per-spawn token cost that nothing else bounds. Its own
 * rule — «current state + rules + pointers only; history lives in `ai-context/decisions.md`» — has been
 * in force since Этап 0.4 (2026-08-19, 101 KB → ~15 KB), and twelve weeks later the file stood at
 * 98 KB again: every block close appended narrative, nobody re-read it, and a rule with no mechanism
 * behind it did not hold. The second compression (2026-11-11, 98 KB → ~42 KB) would decay the same way.
 * This guard is the mechanism: the build goes red when the file grows past the ceiling.
 *
 * **What a red guard means.** Not «raise the ceiling» — «move text». Status and a pointer stay in
 * `CLAUDE.md`; the narrative goes to the block's ADR or to `§HANDOFF`. Raising the constant is a
 * change to a governing rule and needs an owner decision, said in the commit.
 *
 * **Why bytes, not lines or tokens.** Bytes are what the file costs on disk and what `wc -c` reports in
 * every measurement this project has recorded; Cyrillic costs two bytes a character, which is the
 * honest direction (it also costs more tokens). A token count would need a tokenizer this build does
 * not have, and a line count is defeated by long lines.
 *
 * **Declared input.** `app/build.gradle.kts` declares `CLAUDE.md` as an input of every `Test` task, so
 * an edit to it re-runs this task instead of being served `FROM-CACHE` — the same trap and the same fix
 * as [com.sidr.launcher.doctrine.DoctrineMatrixGuardTest]'s matrix file.
 *
 * Working directory is the module dir (`app`), so the repo root is `..`.
 */
class ClaudeMdSizeGuardTest {

    private val repoRoot = File("..")
    private val claudeMd = File(repoRoot, "CLAUDE.md")

    @Test
    fun `CLAUDE md exists at the repo root`() {
        assertTrue(
            "CLAUDE.md not found at ${claudeMd.absolutePath}. If it moved, this guard moves with it; " +
                "an absent file must not pass as a small one.",
            claudeMd.isFile,
        )
    }

    @Test
    fun `CLAUDE md stays under its size ceiling`() {
        val bytes = claudeMd.length()
        assertTrue(
            "CLAUDE.md is $bytes bytes, over the ceiling of $CEILING_BYTES. Do not raise the ceiling: " +
                "move narrative out of the file — a line of status and a pointer stay, the full text goes " +
                "to the block's ADR in ai-context/decisions.md or to §HANDOFF of the track plan " +
                "(precedents: Этап 0.4, 2026-08-19; context hygiene, 2026-11-11).",
            bytes <= CEILING_BYTES,
        )
    }

    private companion object {
        /**
         * 48 000 bytes: the 2026-11-11 compression landed at ~42 KB with the five verbatim rule sections
         * (Hard rules, Build & verification gate, Contract → Owner module, Do not, Source of truth) at
         * ~24 KB of that, so the ceiling leaves about 6 KB — a few block closes of status lines — before
         * the next move of text is due. Raising it is an owner decision.
         */
        const val CEILING_BYTES = 48_000L
    }
}
