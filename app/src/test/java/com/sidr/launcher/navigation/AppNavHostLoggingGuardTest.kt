package com.sidr.launcher.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavHostLoggingGuardTest {

    @Test
    fun `navigation fallback log is payload-free`() {
        val source = appNavHostSource().readText()
        val logLines = source.lines().filter { it.contains("Log.") }

        assertTrue(
            "Expected AppNavHost to keep a payload-free navigation fallback warning",
            logLines.any {
                it.contains("Navigation route was not registered; falling back to launcher home.")
            },
        )
        assertFalse(
            "Navigation logs must not include raw routes; assistant routes can carry prompt text",
            logLines.any { it.contains("event.route") },
        )
        assertFalse(
            "Navigation fallback must not attach the exception; Navigation may include the raw route",
            logLines.any { it.contains(", e)") || it.contains(", throwable") || it.contains(", t)") },
        )
    }

    private fun appNavHostSource(): File {
        val relativePaths = listOf(
            "app/src/main/java/com/sidr/launcher/navigation/AppNavHost.kt",
            "src/main/java/com/sidr/launcher/navigation/AppNavHost.kt",
        )
        val start = File("").canonicalFile
        return generateSequence(start) { it.parentFile }
            .flatMap { dir -> relativePaths.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile }
            ?: error("Could not locate AppNavHost.kt from $start")
    }
}
