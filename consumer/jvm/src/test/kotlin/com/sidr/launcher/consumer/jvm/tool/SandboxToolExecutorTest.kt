package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SandboxToolExecutorTest {

    @get:Rule val temp = TemporaryFolder()

    private fun executor() = SandboxToolExecutor(temp.root.toPath())

    private fun rootArg() = temp.root.toPath().toAbsolutePath().toString()

    @Test
    fun `workspace_info reports the sandbox root`() = runTest {
        val result = executor().invoke(ResolvedInvocation(SandboxToolIds.WORKSPACE_INFO))
        val effected = result as ToolResult.Effected
        assertEquals(rootArg(), effected.output.values[SandboxKeys.ROOT])
    }

    @Test
    fun `find_file returns the path of a match`() = runTest {
        val file = temp.newFile("stale.lock")
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "stale.lock", SandboxKeys.ROOT to rootArg()),
            ),
        )
        val effected = result as ToolResult.Effected
        assertEquals(file.toPath().toAbsolutePath().toString(), effected.output.values[SandboxKeys.RESOLVED_PATH])
    }

    /**
     * The miss branch still emits the declared key, blank. `ToolDescriptor` requires a declared output
     * to be a property of the tool and not of the branch it took, and a blank value is not a loophole:
     * `InvocationValidator.resolve` applies `.takeIf { it.isNotBlank() }`, so the binding fails closed.
     */
    @Test
    fun `find_file emits the declared key blank when nothing matched`() = runTest {
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "absent.lock", SandboxKeys.ROOT to rootArg()),
            ),
        )
        val effected = result as ToolResult.Effected
        assertEquals(setOf(SandboxKeys.RESOLVED_PATH), effected.output.values.keys)
        assertEquals("", effected.output.values[SandboxKeys.RESOLVED_PATH])
    }

    @Test
    fun `delete_file deletes a file inside the sandbox`() = runTest {
        val file = temp.newFile("stale.lock")
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.DELETE_FILE,
                mapOf(SandboxKeys.PATH to file.toPath().toAbsolutePath().toString()),
            ),
        )
        assertTrue(result is ToolResult.Effected)
        assertTrue("the file is gone", !file.exists())
    }

    /**
     * The sandbox boundary. A tool is the only path to the world, so its own containment check is the
     * last thing between a bound argument and an irreversible act. Refusal is a `Failed` result, never
     * an exception: an escaping path is a fact the trace must carry, not a crash.
     */
    @Test
    fun `delete_file refuses a path outside the sandbox and deletes nothing`() = runTest {
        val outside = Files.createTempFile("outside", ".lock")
        try {
            val result = executor().invoke(
                ResolvedInvocation(
                    SandboxToolIds.DELETE_FILE,
                    mapOf(SandboxKeys.PATH to outside.toAbsolutePath().toString()),
                ),
            )
            assertTrue("an escaping path must fail, not delete", result is ToolResult.Failed)
            assertTrue("the outside file survives", Files.exists(outside))
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `delete_file refuses a dot-dot escape even when it names a real file`() = runTest {
        val outside = Files.createTempFile("outside", ".lock")
        try {
            val escape = temp.root.toPath().resolve("..").resolve(outside.fileName).toString()
            val result = executor().invoke(
                ResolvedInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to escape)),
            )
            assertTrue(result is ToolResult.Failed)
            assertTrue("the outside file survives", Files.exists(outside))
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `an unknown tool id fails closed`() = runTest {
        val result = executor().invoke(ResolvedInvocation(com.sidr.launcher.domain.tool.ToolId("nope")))
        assertTrue(result is ToolResult.Failed)
    }
}
