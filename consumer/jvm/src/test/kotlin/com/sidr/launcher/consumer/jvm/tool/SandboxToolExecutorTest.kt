package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SandboxToolExecutorTest {

    @get:Rule val temp = TemporaryFolder()

    private fun executor() = SandboxToolExecutor(temp.root.toPath())

    // F8: canonicalized, matching what the executor itself emits (canonicalRoot() = toRealPath()).
    // Green on a machine where the temp root is not itself a symlink coincides with green on one where
    // it is (e.g. macOS, where TemporaryFolder lands under /var -> /private/var) only if this call does
    // the same resolution the production code does, rather than relying on the coincidence.
    private fun rootArg() = temp.root.toPath().toRealPath().toString()

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

    /**
     * F1 regression. Before the fix, `contained()` only resolved symlinks when the candidate already
     * existed on disk, so a non-existent leaf reached through a directory symlink normalised to a path
     * that still *looked* like it was inside the sandbox textually, while any real filesystem call
     * would in fact resolve it outside. `realPath()` now resolves the nearest existing ancestor — here,
     * the symlink itself — before the comparison, regardless of whether the leaf exists.
     */
    @Test
    fun `delete_file refuses an escape through an intermediate symlink even when the leaf does not exist`() = runTest {
        val outsideDir = Files.createTempDirectory("outside-dir")
        try {
            val link = temp.root.toPath().resolve("dlnk")
            Files.createSymbolicLink(link, outsideDir)
            val ghost = link.resolve("ghost.txt").toString()
            val result = executor().invoke(
                ResolvedInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to ghost)),
            )
            assertTrue("a leaf under an escaping symlink must fail even when absent", result is ToolResult.Failed)
        } finally {
            Files.deleteIfExists(outsideDir)
        }
    }

    /**
     * F2. `canonicalRoot()` used to throw `NoSuchFileException` straight through `invoke` when the
     * sandbox root itself was gone — a `/tmp` sweep, a reboot, or the user deleting the directory
     * mid-plan, all non-racy. `invoke`'s top-level catch converts it to a `Failed` result instead.
     */
    @Test
    fun `workspace_info fails closed when the sandbox root no longer exists`() = runTest {
        val gone = temp.newFolder("gone").toPath()
        Files.delete(gone)
        val result = SandboxToolExecutor(gone).invoke(ResolvedInvocation(SandboxToolIds.WORKSPACE_INFO))
        assertTrue(result is ToolResult.Failed)
    }

    /**
     * F4. `Paths.get(raw)` throws the unchecked `InvalidPathException` before `contained()`'s own
     * `takeIf` can refuse anything — a NUL byte is one reliable way to trigger it on Linux.
     */
    @Test
    fun `find_file fails closed on an argument that is not a valid path`() = runTest {
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "x", SandboxKeys.ROOT to "bad\u0000path"),
            ),
        )
        assertTrue(result is ToolResult.Failed)
    }

    /**
     * F3. `Files.walk` throws `UncheckedIOException` at `findFirst()` over an unreadable subdirectory —
     * `Files.isDirectory` at the top of `findFile` passes regardless, because `stat` succeeds where
     * traversal does not. Not racy, but not reproducible everywhere: a test process running as root, or
     * a filesystem that ignores POSIX permissions, cannot make a directory unreadable to itself. Rather
     * than ship a test that passes for the wrong reason there, or fails flakily, `assumeTrue` turns
     * those environments into a reported skip.
     */
    @Test
    fun `find_file fails closed when a subdirectory is unreadable`() = runTest {
        val locked = temp.newFolder("locked")
        val changed = locked.setReadable(false) && locked.setExecutable(false)
        try {
            assumeTrue(
                "cannot make a directory unreadable in this environment (root, or a POSIX-ignoring filesystem)",
                changed && !Files.isReadable(locked.toPath()),
            )
            val result = executor().invoke(
                ResolvedInvocation(
                    SandboxToolIds.FIND_FILE,
                    mapOf(SandboxKeys.QUERY to "x", SandboxKeys.ROOT to rootArg()),
                ),
            )
            assertTrue(result is ToolResult.Failed)
        } finally {
            locked.setExecutable(true)
            locked.setReadable(true)
        }
    }

    /**
     * F6. `find_file`'s own containment refusal, held by nothing before this test: deleting the
     * `contained(...)` wrapper at the top of `findFile` left all 15 module tests green, because
     * nothing exercised a `root` argument pointing outside the sandbox. Spec §5: "every tool resolves
     * its paths against it and refuses any path that escapes it."
     */
    @Test
    fun `find_file refuses a root argument outside the sandbox`() = runTest {
        val outside = Files.createTempDirectory("outside-root")
        try {
            val result = executor().invoke(
                ResolvedInvocation(
                    SandboxToolIds.FIND_FILE,
                    mapOf(SandboxKeys.QUERY to "x", SandboxKeys.ROOT to outside.toString()),
                ),
            )
            assertTrue("a root argument outside the sandbox must be refused", result is ToolResult.Failed)
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    /** F6, the sibling branch: a `root` argument inside the sandbox that names a file, not a directory. */
    @Test
    fun `find_file fails closed when the root argument is not a directory`() = runTest {
        val file = temp.newFile("not-a-dir")
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "x", SandboxKeys.ROOT to file.toPath().toAbsolutePath().toString()),
            ),
        )
        assertTrue(result is ToolResult.Failed)
    }
}
