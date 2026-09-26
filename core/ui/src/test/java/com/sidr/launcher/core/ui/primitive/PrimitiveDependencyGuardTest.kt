package com.sidr.launcher.core.ui.primitive

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DS-2 architecture guard: `core/ui` primitives are presentation-only. No `domain`/`feature` import may
 * leak in (Master Plan "DS-2 не должен: импортировать domain в core/ui"). Reads the real source dir; the
 * unit-test JVM's working directory is the module dir (`core/ui`).
 */
class PrimitiveDependencyGuardTest {
    @Test fun no_domain_or_feature_import_in_primitives() {
        val dir = File("src/main/java/com/sidr/launcher/core/ui/primitive")
        assertTrue("primitive source dir must exist: ${dir.absolutePath}", dir.isDirectory)
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import ") }
                    .filter { it.contains("com.sidr.launcher.domain") || it.contains("com.sidr.launcher.feature") }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()
        assertTrue(
            "presentation primitives must not import domain/feature:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
