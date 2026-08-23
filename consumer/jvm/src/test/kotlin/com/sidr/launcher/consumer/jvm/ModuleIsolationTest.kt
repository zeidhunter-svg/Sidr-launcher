package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two properties that make this module the *second consumer* rather than a second Android module.
 *
 * The first is a compile-time claim as much as a runtime one: if `:domain` were not on this module's
 * classpath, this file would not compile, so the assertions are the visible half of a check the
 * compiler already made.
 *
 * The second is the one worth a test. `:consumer:jvm` must reach the portable engine and **nothing**
 * Android. A dependency added by accident — on `:core:android`, on `:data:repository`, on AGP — would
 * make `android.content.Intent` loadable, and this test is what says so out loud.
 */
class ModuleIsolationTest {

    @Test
    fun `the portable engine contracts are on this module's classpath`() {
        assertEquals(8, RuntimeBudget.Default.maxSteps)
        assertEquals(2, RuntimeBudget.Default.maxConsecutiveFailures)
        assertEquals("workspace_info", ToolId("workspace_info").value)
    }

    @Test
    fun `no Android class is reachable from this module`() {
        val android = runCatching { Class.forName("android.content.Intent") }
        assertTrue(
            "android.* must not be reachable from :consumer:jvm — the second consumer proves the core " +
                "is portable only if it has no Android on its classpath. Found: ${android.getOrNull()}",
            android.isFailure,
        )
    }
}
