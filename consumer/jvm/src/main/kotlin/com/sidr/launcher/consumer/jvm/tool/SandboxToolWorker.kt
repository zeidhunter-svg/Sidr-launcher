package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.CancellationException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The second consumer's worker: what actually performs a tool call for its registered adapter in
 * `ToolFederation`, this repository's **one and only** [com.sidr.launcher.domain.tool.ToolExecutor]
 * implementation. This is not a second boundary — a [ToolWorker] is reachable only from that
 * dispatcher's `adapter.worker.invoke(invocation)`, never called directly in production, and every
 * call still traces back to `AgentExecutor.perform`, the single call site below the consent
 * checkpoint. (Tests call [invoke] directly — `SandboxToolWorkerTest` and `SandboxToolContractTest`
 * — which is the normal way to unit-test a class, not a second production path.) It sits inside the
 * **same** mechanical boundary as the Android worker, not outside it: `ToolWorkerCallSiteGuardTest`
 * scans `consumer/jvm/src/main/kotlin` as one of its production roots, and this file is one of the
 * declared `ToolWorker` holders it pins by name (with `ToolFederation.kt`, `SystemIntentToolWorker.kt`
 * and, since A1' Task 6, `Tier0IntentToolWorker.kt`). Three workers are legitimate — this class,
 * `SystemIntentToolWorker` and `Tier0IntentToolWorker`, alongside `ToolFederation.kt`'s own
 * `val worker: ToolWorker` field declaration, which the guard pins as a fourth holder for the same
 * reason; the property the guard holds is **one call site to a worker**, which federation guards as a
 * second, equally strict hop rather than a weaker one.
 *
 * Every failure this class can produce — a declined containment check, or an exception escaping the
 * filesystem calls below (a vanished root, an unreadable directory, an invalid path argument) — is
 * converted to a [ToolResult.Failed] before it leaves [invoke]. Cancellation is rethrown, not
 * swallowed, per this repository's precedent (`RoomAgentSessionStore.guarded`).
 *
 * **A recorded limitation, and a finding of the block (spec §3.4):** every failure here reports
 * [CommandFailure.Generic], because `CommandFailure` is a closed five-value type whose other four
 * values are launcher-shaped (`CantOpenApp`, `NoSearchApp`, `CantOpenUrl`, `NoStoreApp`). A PC tool
 * cannot say "permission denied" or "outside the sandbox" in the vocabulary the core gives it. Owned
 * by A1'; **not** repaired here (Approach A, spec §2).
 */
class SandboxToolWorker(private val root: Path) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = try {
        when (invocation.id) {
            SandboxToolIds.WORKSPACE_INFO -> ToolResult.Effected(
                ToolOutput(mapOf(SandboxKeys.ROOT to canonicalRoot().toString())),
            )
            SandboxToolIds.FIND_FILE -> findFile(invocation)
            SandboxToolIds.DELETE_FILE -> deleteFile(invocation)
            // A tool this executor does not implement must not silently succeed. The registry and the
            // executor are separate objects, so "registered but unimplemented" is a reachable state.
            else -> ToolResult.Failed(CommandFailure.Generic)
        }
    } catch (e: CancellationException) {
        // invoke() is suspend; a bare catch-all below would swallow coroutine cancellation instead of
        // propagating it. Rethrow first, exactly as RoomAgentSessionStore.guarded does.
        throw e
    } catch (e: Exception) {
        // Everything this class can still throw once past the checks above — NoSuchFileException from
        // a root deleted mid-session, an (Unchecked)IOException from Files.walk over an unreadable
        // subdirectory, InvalidPathException from a raw argument containing e.g. a NUL byte — is a
        // fact about the world, not a crash. It becomes the same Failed result a declined containment
        // check would have produced.
        ToolResult.Failed(CommandFailure.Generic)
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
        // NOFOLLOW_LINKS: target is already `contained()`'s fully-resolved real path, so there is no
        // symlink left *in* it, but the leaf itself could still be a symlink (its own real path is
        // itself, by definition — resolving a symlink node does not remove the node). A symlink is
        // never treated as a regular file to delete.
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return ToolResult.Failed(CommandFailure.Generic)
        }
        return if (Files.deleteIfExists(target)) {
            ToolResult.Effected()
        } else {
            ToolResult.Failed(CommandFailure.Generic)
        }
    }

    private fun canonicalRoot(): Path = root.toAbsolutePath().normalize().toRealPath()

    /**
     * The sandbox boundary. The candidate is normalised first, then resolved to its real path via
     * [realPath] — which walks up to the nearest existing ancestor and resolves symlinks **there**
     * even when the leaf itself does not exist yet, so a directory symlink in the middle of the path
     * cannot smuggle a non-existent leaf outside the sandbox (F1). A non-existent leaf is still a
     * legitimate argument — a tool about to report "nothing there" — so this never rejects one on
     * that basis alone; it only refuses to trust a resolved ancestor outside the root.
     *
     * Returns `null` for anything outside, so the caller fails the step rather than acting.
     */
    private fun contained(raw: String): Path? {
        if (raw.isBlank()) return null
        val base = canonicalRoot()
        val candidate = Paths.get(raw).toAbsolutePath().normalize()
        val real = realPath(candidate)
        return real.takeIf { it == base || it.startsWith(base) }
    }

    /**
     * [Path.toRealPath] resolves symlinks but throws when the path does not exist. This resolves the
     * nearest existing ancestor instead — which may be several levels up — and re-attaches the missing
     * tail unresolved, since there is nothing on disk there to resolve. `candidate` is always already
     * normalised by the caller, so the tail carries no `..` to re-interpret.
     *
     * Iterative, not recursive (fix for a regression this same round introduced): one stack frame per
     * path component would overflow on a deep-enough argument — observed at depth 12,000+ — and
     * `StackOverflowError` is an `Error`, so it would escape `invoke`'s `catch (e: Exception)` and throw
     * straight out of the only path to the world. A `while` loop bounds this by heap, not stack.
     */
    private fun realPath(candidate: Path): Path {
        if (Files.exists(candidate)) return candidate.toRealPath()
        val missingTail = ArrayDeque<Path>()
        var cursor = candidate
        while (!Files.exists(cursor)) {
            missingTail.addFirst(cursor.fileName)
            cursor = cursor.parent ?: return candidate
        }
        var resolved = cursor.toRealPath()
        missingTail.forEach { resolved = resolved.resolve(it) }
        return resolved
    }
}
