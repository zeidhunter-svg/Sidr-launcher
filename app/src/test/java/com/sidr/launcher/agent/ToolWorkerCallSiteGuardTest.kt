package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **The second hop, held the same way as the first.** `ToolExecutorCallSiteGuardTest` holds that the
 * engine reaches the world through one call in one file. Federation put a dispatcher below that call,
 * so "one path to the world" is now two hops, and a hop nobody guards is a hop where a call site can
 * appear. This guard is the second half; neither is sufficient alone.
 *
 * It asserts, over comment-stripped production text across the same roots:
 *  1. exactly one call spelled `worker.invoke(`, and that it lives in `ToolFederation.kt`;
 *  2. exactly the known set of files declaring `: ToolWorker` — the adapter type itself plus one
 *     worker per registered source. A new source adds one entry here and nowhere else, which is the
 *     growth rule's promise made checkable: a worker cannot be reached except through the dispatcher.
 *
 * `D2` predicted this shape («регекс держателя не видит `Map<…, ToolExecutor>` — вероятная форма A1′»)
 * and it is closed by construction rather than by a wider regex: the dispatcher holds *workers*, and
 * the map it holds them in is `ToolFederation`'s own.
 *
 * **The holder list is three today, not four.** A1′ Task 6 adds `Tier0IntentToolWorker.kt` as the
 * fourth registered worker; until that task lands, this guard holds the two workers this block
 * actually ships (`SandboxToolWorker`, `SystemIntentToolWorker`) plus the dispatcher itself.
 *
 * **Named, not closed:** these roots are the same five `ToolExecutorCallSiteGuardTest` walks, and
 * carry the same fail-open direction that guard's own KDoc names — in particular, `core/testing`'s
 * `src/main/java` is not one of them, so a `ToolWorker` declared there (a hand-rolled fake, say) would
 * be invisible to this scan. Widening the roots is the same owner-level build trade-off parked beside
 * findings `D1`/`D4`: it costs another repo-wide snapshot per test task, and is not done here.
 */
class ToolWorkerCallSiteGuardTest {

    private val repoRoot = File("..")

    private val productionRoots: List<File> =
        kmpProductionRoots(File(repoRoot, "domain/src")) +
            listOf(
                "data/repository/src/main/java",
                "feature/launcher/src/main/java",
                "app/src/main/java",
                "consumer/jvm/src/main/kotlin",
            ).map { File(repoRoot, it) }

    private val declaresToolWorker = Regex(""":\s*ToolWorker\b""")

    private fun productionSources(): List<File> =
        productionRoots.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }

    @Test
    fun `scanned roots all exist`() {
        assertEquals(
            "no :domain source root was derived — this guard went blind to the module that declares " +
                "ToolWorker",
            true,
            kmpProductionRoots(File(repoRoot, "domain/src")).isNotEmpty(),
        )
        productionRoots.forEach { root ->
            assertEquals("missing scan root: $root", true, root.isDirectory)
        }
    }

    @Test
    fun `there is exactly one call site of ToolWorker invoke in production code`() {
        val hits = productionSources()
            .flatMap { file ->
                stripComments(file.readText())
                    .lines()
                    .withIndex()
                    .filter { (_, line) -> line.contains("worker.invoke(") }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }

        assertEquals(
            "A ToolWorker may be reached only from the dispatcher. Found: $hits",
            1,
            hits.size,
        )
    }

    @Test
    fun `the one worker call site lives in ToolFederation`() {
        val files = productionSources()
            .filter { stripComments(it.readText()).contains("worker.invoke(") }
            .map { it.name }

        assertEquals(listOf("ToolFederation.kt"), files)
    }

    /**
     * The three, and why each is legitimate: `SandboxToolWorker.kt` (the second consumer's worker),
     * `SystemIntentToolWorker.kt` (the Android worker) and `ToolFederation.kt` (the adapter type
     * itself, `val worker: ToolWorker` on `ToolAdapter`). A1′ Task 6 adds `Tier0IntentToolWorker.kt` as
     * a fourth in its own commit — see the class KDoc.
     */
    @Test
    fun `the declared holders of a ToolWorker are exactly the known three`() {
        val files = productionSources()
            .filter { declaresToolWorker.containsMatchIn(stripComments(it.readText())) }
            .map { it.name }
            .sorted()

        assertEquals(
            "A new ToolWorker declaration is a new path to the world. It is legitimate only as a " +
                "registered adapter's worker; if this list grew without a matching adapter, the " +
                "growth rule was bypassed. Found: $files",
            listOf(
                "SandboxToolWorker.kt",
                "SystemIntentToolWorker.kt",
                "ToolFederation.kt",
            ),
            files,
        )
    }
}
