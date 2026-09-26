package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third adapter's execution face. The Android call (`LauncherApps.startShortcut`) sits behind
 * [ShortcutLauncher], so these tests reach every branch without a device — and, as A1′'s own
 * `set_timer` defect proved, that also means they can say nothing about whether the real call is
 * permitted. What governs that is measured, not tested:
 * `docs/superpowers/plans/2026-09-12-a1-device-measurements.md` rows 6 and 10.
 */
class ShortcutToolWorkerTest {

    private class RecordingShortcutLauncher : ShortcutLauncher {
        val started = mutableListOf<Pair<String, String>>()
        override fun start(packageName: String, shortcutId: String) {
            started += packageName to shortcutId
        }
    }

    private fun resolved(id: ToolId) = ResolvedInvocation(id)

    @Test
    fun `a parseable id starts exactly that shortcut and reports an effect`() = runTest {
        val launcher = RecordingShortcutLauncher()

        val result = ShortcutToolWorker(launcher).invoke(resolved(ToolId("shortcut:com.a/new_chat")))

        assertEquals(listOf("com.a" to "new_chat"), launcher.started)
        assertEquals(ToolResult.Effected(ToolOutput()), result)
    }

    @Test
    fun `an unparseable id fails closed and starts nothing`() = runTest {
        val launcher = RecordingShortcutLauncher()

        val result = ShortcutToolWorker(launcher).invoke(resolved(ToolId("set_timer")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(CommandFailure.Generic, (result as ToolResult.Failed).failure)
        assertTrue(launcher.started.isEmpty())
    }

    @Test
    fun `a launcher that throws yields Failed rather than propagating`() = runTest {
        val throwing = ShortcutLauncher { _, _ -> throw IllegalStateException("shortcut is gone") }

        val result = ShortcutToolWorker(throwing).invoke(resolved(ToolId("shortcut:com.a/new_chat")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(CommandFailure.Generic, (result as ToolResult.Failed).failure)
    }

    /**
     * The one the measurement file actually predicts: the HOME role can be taken away between the
     * snapshot that advertised this tool and the moment it runs (rows 1, 2 and 6). `getShortcuts`
     * throws `SecurityException` across that boundary; what `startShortcut` does is **unmeasured**, so
     * containment here is broad by decision rather than narrow by measurement.
     */
    @Test
    fun `a security failure across the HOME-role boundary is contained, not propagated`() = runTest {
        val refusing = ShortcutLauncher { _, _ -> throw SecurityException("Caller can't access shortcut information") }

        val result = ShortcutToolWorker(refusing).invoke(resolved(ToolId("shortcut:com.a/new_chat")))

        assertTrue(result is ToolResult.Failed)
    }
}
