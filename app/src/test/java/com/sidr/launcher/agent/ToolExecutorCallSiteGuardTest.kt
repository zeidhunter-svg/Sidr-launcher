package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **The consent boundary, expressed mechanically.** The growth rule promises that tool #21 gets consent
 * for free. That promise is only true if there is exactly one place where a tool can be invoked, and
 * that place sits below the checkpoint. This test counts those places.
 *
 * It is a textual scan, deliberately: an architectural claim that a *reader* can check in ten seconds
 * is worth more here than one that needs a bytecode analyser. Working directory is the module dir, so
 * the repo root is `..` — same convention as the i18n and doctrine guards.
 *
 * The roots it walks are declared as `Test` inputs in `app/build.gradle.kts`; without that the task
 * stays UP-TO-DATE when only a scanned source changes and this guard silently does not run.
 */
class ToolExecutorCallSiteGuardTest {

    private val repoRoot = File("..")

    private val productionRoots = listOf(
        "domain/src/commonMain/kotlin",
        "data/repository/src/main/java",
        "feature/launcher/src/main/java",
        "app/src/main/java",
    ).map { File(repoRoot, it) }

    @Test
    fun `scanned roots all exist`() {
        // A guard whose walk finds nothing passes vacuously — the exact failure Этап 2 found in three
        // privacy guards. Assert the roots first.
        productionRoots.forEach { root ->
            assertEquals("missing scan root: $root", true, root.isDirectory)
        }
    }

    @Test
    fun `there is exactly one call site of ToolExecutor invoke in production code`() {
        val hits = productionRoots
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> line.contains("toolExecutor.invoke(") }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }

        assertEquals(
            "ToolExecutor.invoke must have exactly one call site, below the consent checkpoint. Found: $hits",
            1,
            hits.size,
        )
    }

    @Test
    fun `the one call site lives in AgentExecutor`() {
        val files = productionRoots
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } }
            .filter { it.readText().contains("toolExecutor.invoke(") }
            .map { it.name }

        assertEquals(listOf("AgentExecutor.kt"), files)
    }
}
