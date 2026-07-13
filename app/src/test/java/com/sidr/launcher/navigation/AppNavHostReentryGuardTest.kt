package com.sidr.launcher.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavHostReentryGuardTest {

    @Test
    fun `launcher re-entry resets nav host to home`() {
        val manifest = projectSource("app/src/main/AndroidManifest.xml").readText()
        val activity = projectSource("app/src/main/java/com/sidr/launcher/LauncherActivity.kt").readText()
        val navHost = projectSource("app/src/main/java/com/sidr/launcher/navigation/AppNavHost.kt").readText()

        assertTrue(
            "LauncherActivity must receive a new intent while it is already alive",
            manifest.contains("android:launchMode=\"singleTop\""),
        )
        assertTrue(
            "LauncherActivity must handle re-entry intents",
            activity.contains("override fun onNewIntent(intent: Intent)"),
        )
        assertTrue(
            "LauncherActivity should keep the latest intent available",
            activity.contains("setIntent(intent)"),
        )
        assertTrue(
            "LauncherActivity must pass the re-entry signal into AppNavHost",
            // Argument-level check: the AppNavHost call gained more parameters (alwaysShowNav,
            // 2026-07-12), so the guard must not pin the exact single-argument call syntax.
            activity.contains("homeResetSignal = homeResetSignal"),
        )
        assertTrue(
            "AppNavHost must expose a home reset signal",
            navHost.contains("homeResetSignal: Int = 0"),
        )
        assertTrue(
            "AppNavHost must react to the home reset signal",
            navHost.contains("LaunchedEffect(homeResetSignal)"),
        )
        assertTrue(
            "AppNavHost must ignore the initial composition and reset only on re-entry",
            navHost.contains("if (homeResetSignal > 0)"),
        )
        assertTrue(
            "Re-entry should clear nested destinations back to launcher home",
            navHost.contains("navigateHome(navController)"),
        )
    }

    private fun projectSource(path: String): File {
        val start = File("").canonicalFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.isFile }
            ?: error("Could not locate $path from $start")
    }
}
