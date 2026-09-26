package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I18N-1 barrier 1b (spec §10.2). `stringResource` / `pluralStringResource` may be called **only**
 * inside the seam (`core/ui/i18n/SidrStrings.kt`). Everything else goes through `sidrString`, which is
 * what keeps the future `translate_ui` overlay a drop-in.
 *
 * Exact, not heuristic - this is the barrier that makes the seam real rather than a convention.
 * The unit-test working directory is the module dir (`app`), so the repo root is `..`.
 */
class StringSeamGuardTest {

    private val seamFile = "core/ui/src/main/java/com/sidr/launcher/core/ui/i18n/SidrStrings.kt"

    @Test fun string_resource_is_called_only_inside_the_seam() {
        val repoRoot = File("..")
        val offenders = repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("/src/main/") && !it.path.contains("/build/") }
            .filterNot { it.canonicalPath.endsWith(seamFile) }
            .flatMap { file ->
                file.readLines()
                    .filter { line ->
                        line.contains("androidx.compose.ui.res.stringResource") ||
                            line.contains("androidx.compose.ui.res.pluralStringResource")
                    }
                    .map { "${file.relativeTo(repoRoot)}: ${it.trim()}" }
            }
            .toList()

        assertTrue(
            "Call sidrString(...) instead of stringResource(...) - the overlay seam must not be " +
                "bypassed (spec §6, §10.2):\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
