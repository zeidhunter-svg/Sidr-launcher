package com.sidr.launcher.core.ui.component

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DS-3 architecture guard: `core/ui/component` SIDR controls are presentation-only. No `domain`/`data`/
 * `feature` import may leak in (spec §1, §5: "no domain or feature imports in core/ui/component controls").
 * Reads the real source dir; the unit-test JVM's working directory is the module dir (`core/ui`).
 *
 * Narrowed to `Sidr*.kt` files so existing legacy components (e.g. `ConfirmActionCard`, `RouteChipRow`)
 * are not accidentally flagged unless they are part of DS-3.
 */
class ControlsDependencyGuardTest {
    @Test fun no_domain_data_or_feature_import_in_sidr_controls() {
        val dir = File("src/main/java/com/sidr/launcher/core/ui/component")
        assertTrue("component source dir must exist: ${dir.absolutePath}", dir.isDirectory)
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name.startsWith("Sidr") }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import ") }
                    .filter {
                        it.contains("com.sidr.launcher.domain") ||
                            it.contains("com.sidr.launcher.data") ||
                            it.contains("com.sidr.launcher.feature")
                    }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()
        assertTrue(
            "SIDR controls must not import domain/data/feature:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
