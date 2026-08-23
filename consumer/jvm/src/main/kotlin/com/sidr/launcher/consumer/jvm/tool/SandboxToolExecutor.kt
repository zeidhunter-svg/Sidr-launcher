package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The second consumer's **only path to the world**, and the second implementation of [ToolExecutor] in
 * this repository. `ToolExecutorCallSiteGuardTest` (Task 9) is extended to see this file: it becomes
 * the fourth declared holder while the call-site count stays at **one**, because this class is invoked
 * by `AgentExecutor` and never by the harness directly.
 *
 * Every failure is a [ToolResult.Failed], never a thrown exception — an escaping path or a vanished
 * file is a fact the trace must carry.
 *
 * **A recorded limitation, and a finding of the block (spec §3.4):** every failure here reports
 * [CommandFailure.Generic], because `CommandFailure` is a closed five-value type whose other four
 * values are launcher-shaped (`CantOpenApp`, `NoSearchApp`, `CantOpenUrl`, `NoStoreApp`). A PC tool
 * cannot say "permission denied" or "outside the sandbox" in the vocabulary the core gives it. Owned
 * by A1'; **not** repaired here (Approach A, spec §2).
 */
class SandboxToolExecutor(private val root: Path) : ToolExecutor {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = when (invocation.id) {
        SandboxToolIds.WORKSPACE_INFO -> ToolResult.Effected(
            ToolOutput(mapOf(SandboxKeys.ROOT to canonicalRoot().toString())),
        )
        SandboxToolIds.FIND_FILE -> findFile(invocation)
        SandboxToolIds.DELETE_FILE -> deleteFile(invocation)
        // A tool this executor does not implement must not silently succeed. The registry and the
        // executor are separate objects, so "registered but unimplemented" is a reachable state.
        else -> ToolResult.Failed(CommandFailure.Generic)
    }

    private fun findFile(invocation: ResolvedInvocation): ToolResult {
        val query = invocation.args[SandboxKeys.QUERY].orEmpty()
        val dir = contained(invocation.args[SandboxKeys.ROOT].orEmpty())
            ?: return ToolResult.Failed(CommandFailure.Generic)
        if (!Files.isDirectory(dir)) return ToolResult.Failed(CommandFailure.Generic)

        val match = Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString() == query }
                .findFirst()
                .orElse(null)
        }

        // The declared key is emitted on EVERY branch — blank when nothing matched. `ToolDescriptor`:
        // "outputs are a property of the tool, not of the branch it happened to take, and a schema
        // that only sometimes holds is not a schema." The blank is fail-closed downstream, because
        // `InvocationValidator.resolve` rejects a blank binding as UNRESOLVED_ARG_SOURCE.
        return ToolResult.Effected(
            ToolOutput(mapOf(SandboxKeys.RESOLVED_PATH to (match?.toAbsolutePath()?.toString() ?: ""))),
        )
    }

    private fun deleteFile(invocation: ResolvedInvocation): ToolResult {
        val target = contained(invocation.args[SandboxKeys.PATH].orEmpty())
            ?: return ToolResult.Failed(CommandFailure.Generic)
        if (!Files.isRegularFile(target)) return ToolResult.Failed(CommandFailure.Generic)
        return if (Files.deleteIfExists(target)) {
            ToolResult.Effected()
        } else {
            ToolResult.Failed(CommandFailure.Generic)
        }
    }

    private fun canonicalRoot(): Path = root.toAbsolutePath().normalize().toRealPath()

    /**
     * The sandbox boundary. `..` is normalised and symlinks are resolved **before** the comparison, so
     * neither can walk out. A path that does not exist yet is compared after normalisation only —
     * `toRealPath` would throw on it, and a non-existent path is a legitimate argument to a tool that
     * is about to report "nothing there".
     *
     * Returns `null` for anything outside, so the caller fails the step rather than acting.
     */
    private fun contained(raw: String): Path? {
        if (raw.isBlank()) return null
        val base = canonicalRoot()
        val candidate = Paths.get(raw).toAbsolutePath().normalize()
        val real = if (Files.exists(candidate)) candidate.toRealPath() else candidate
        return real.takeIf { it == base || it.startsWith(base) }
    }
}
