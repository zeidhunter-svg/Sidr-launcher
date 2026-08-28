package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/** A persisted session that cannot be decoded. Carries no detail that could reach an [OperationError]. */
internal class CorruptSessionFileException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The second consumer's [AgentSessionStore]: one JSON file holding at most one session.
 *
 * **No history at rest — but this class is not the thing that decides it.** Nothing here inspects
 * [ExecutionState.isTerminal]. The deletion is `RunAgentSessionUseCase`'s: it saves the terminal
 * session and then calls [delete], so a finished session exists on disk briefly, holding the raw goal
 * text, before it is removed. What this class guarantees is narrower and is all it can guarantee —
 * [delete] leaves no file behind, and [active] then reports no session. A durable trace journal is A5's
 * decision, not one A0.5 quietly introduces.
 *
 * **[recordConsentIfPending] is a real compare-and-set**, and that is the point of this class. The
 * contract exists because Room can say `UPDATE … WHERE state = pending` in one statement; honouring it
 * here without weakening it to a whole-object write is what decides whether `AgentSessionStore` is a
 * portable port or a Room shape wearing an interface (spec §8, §11.2). Two mechanisms, because they
 * exclude different things: a **process-wide, path-keyed** [Mutex] for coroutines inside this JVM, a
 * [java.nio.channels.FileLock] for a second process opening the same file.
 *
 * **The [java.nio.channels.FileLock] is verified by no test in this repository.** Every test runs in a
 * single JVM, where the gate above makes the lock uncontended — removing the `FileLock` entirely leaves
 * the suite green. It is kept because it is the only mechanism that *can* cover a second process, not
 * because anything proves it does; a forked-JVM test is the follow-up if A5 makes the PC consumer real.
 *
 * **Why the [Mutex] is keyed by path and not simply a field.** The first cut held it per instance,
 * which left a gap neither mechanism covered: two [JvmAgentSessionStore] objects over one file inside
 * one JVM. `FileChannel.lock()` is JVM-wide, so the second acquisition threw
 * `OverlappingFileLockException` instead of blocking, and the losing caller was handed
 * `OperationResult.Failure` where the contract says `Success(false)`. The CAS still failed closed, but
 * "another tap won" and "the store broke" became indistinguishable — which is exactly the weakening
 * spec §8 asks whether a file store can avoid.
 *
 * Whole-object writes were avoided deliberately: that pattern already cost this project the
 * `autoHideNavBar` bug (DS-11), which only surfaced on device.
 *
 * **Named limitations, and this list is meant to be exhaustive.**
 *  - [delete] removes the session file but not its `.lock` sibling, which is created on the first call
 *    and then stays. Nothing reads it as state — [active] keys off the session file alone — so "no
 *    session at rest" holds; "no file at rest" does not. The lock file is always empty.
 *  - A process killed inside [write]'s window — between creating the temp and the `ATOMIC_MOVE` —
 *    leaves a temp file holding a **complete** session, goal text included. [sweepStaleTemps] removes
 *    it, but only on the next [write] or [delete] against the same path. Until one of those runs, that
 *    copy is at rest. Bounding it further would need a sweep on [active] too, which would make a read
 *    delete files; that trade is A5's to make if it ever wants a durable journal.
 *  - **This store's own files are inside the sandbox its tools can reach.** `ConsoleHarness` places the
 *    session at `<root>/.sidr-agent/session.json` and hands `SandboxToolExecutor` the same `<root>`;
 *    `findFile` walks that root with `Files.walk` and skips no hidden directory, and `contained()`
 *    accepts anything under it. So `remove session.json` plans a `DANGEROUS` delete of the agent's own
 *    recovery record — and `remove session.json.lock` of its lock file. The user is still stopped at the
 *    consent gate, so this is reach, not a silent effect. Moving the state directory out of the sandbox
 *    is a design change and not this note's business.
 *  - **No `fsync`.** Neither the temp file nor the parent directory is flushed around the `ATOMIC_MOVE`,
 *    so a power loss (as opposed to a process kill) can leave the rename unflushed, and the outcome is
 *    not one fixed shape. A zero-length `session.json` is the benign case — [read] already treats it as
 *    "no session". But the same unflushed rename can instead leave the **previous** `session.json`
 *    intact, so the next start resumes an older cursor and consent set as if it were current; or it can
 *    leave partial non-blank content, which [read] treats as corrupt, deletes, and reports as
 *    `file_agent_session_corrupt` — a store failure printed rather than "no session". Named here as a
 *    range of outcomes, not as the one this list happened to single out.
 */
class JvmAgentSessionStore(file: Path) : AgentSessionStore {

    /**
     * Absolute and lexically normalised, and used for **every** filesystem operation below.
     *
     * The gate is keyed by this same normalisation, so the path a store locks and the path it writes
     * can never disagree. It also means a bare relative filename — whose `parent` is `null` — has a
     * real parent directory here, which [write] needs for its temp file.
     */
    private val path: Path = file.toAbsolutePath().normalize()

    private val mutex: Mutex = inProcessGate(path)
    private val lockFile: Path = path.resolveSibling(path.fileName.toString() + ".lock")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    override suspend fun active(): OperationResult<AgentSession?> =
        guarded(READ_FAILED) { withFileLock { read() } }

    override suspend fun save(session: AgentSession): OperationResult<Unit> =
        guarded(WRITE_FAILED) { withFileLock { write(session) } }

    override suspend fun delete(id: AgentSessionId): OperationResult<Unit> =
        guarded(DELETE_FAILED) {
            withFileLock {
                if (read()?.id == id) {
                    Files.deleteIfExists(path)
                }
                // Unconditionally, and not only when the id matched: an orphaned temp holds a complete
                // session whoever owns the live file, and `delete` is the moment "nothing at rest" has
                // to be true rather than nearly true.
                sweepStaleTemps()
            }
        }

    override suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean> = guarded(CONSENT_FAILED) {
        withFileLock {
            val current = read()
            when {
                current == null -> false
                current.id != id -> false
                current.state != ExecutionState.AwaitingConsent -> false
                // Parity with the Room sibling, whose `UPDATE … WHERE step_index = :stepIndex` matches
                // no row for a step the plan does not have and therefore reports `false`. Without this
                // the file store would persist a phantom `consents[99]`, and `ResolveConsentUseCase`
                // would flip the session to Running on a decision that decided nothing.
                current.plan.steps.none { it.index == stepIndex } -> false
                // The decisive condition: a step that already carries a decision is no longer
                // pending, so a second tap that raced the first cannot also win.
                current.consents.containsKey(stepIndex) -> false
                else -> {
                    write(current.copy(consents = current.consents + (stepIndex to granted)))
                    true
                }
            }
        }
    }

    /**
     * An undecodable file is **deleted before the failure is reported** — the same reasoning as the
     * Room sibling's F10 fix (2026-08-23). An unreadable recovery record is not a recovery record:
     * nothing can resume it and nothing can show it, so left alone it would sit on disk holding the
     * user's raw goal text until some later session happened to overwrite it. The failure is still
     * reported; a corrupt file never becomes a guessed session.
     */
    private fun read(): AgentSession? {
        if (!Files.exists(path)) return null
        val text = Files.readString(path)
        if (text.isBlank()) return null
        return try {
            SessionMapper.fromDto(json.decodeFromString(SessionDto.serializer(), text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Files.deleteIfExists(path)
            throw CorruptSessionFileException("the persisted session could not be decoded", e)
        }
    }

    /**
     * Write-to-temp then `ATOMIC_MOVE`, never truncate in place. `TRUNCATE_EXISTING` on the live file
     * means a kill between truncate and write leaves **partial JSON** on disk, and [read] treats only a
     * *blank* file as "no session" — so a half-written file would come back as a decode failure on the
     * next start rather than as an absent session.
     */
    private fun write(session: AgentSession) {
        val parent = path.parent
        Files.createDirectories(parent)
        sweepStaleTemps()
        val temp = Files.createTempFile(parent, path.fileName.toString(), TEMP_SUFFIX)
        try {
            Files.writeString(
                temp,
                json.encodeToString(SessionDto.serializer(), SessionMapper.toDto(session)),
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Throwable) {
            Files.deleteIfExists(temp)
            throw e
        }
    }

    /**
     * Removes temp files orphaned by a process that died inside [write]'s window.
     *
     * Safe to delete unconditionally **because every caller holds the file lock**: no other process can
     * be between its own `createTempFile` and `ATOMIC_MOVE` while we hold it, and the in-process gate
     * excludes every other coroutine in this JVM. So a temp seen here belongs to nobody.
     *
     * Matched by our own prefix and suffix with plain string comparison rather than a glob — a glob
     * would give `*`, `?`, `[` and `{` in the session's own file name their pattern meanings and could
     * reach files this store does not own. The `name != live` guard matters for the degenerate case
     * where the session file is itself named `*.tmp`, which would otherwise match itself.
     *
     * Best-effort: a temp that cannot be deleted must not fail the save that was actually asked for.
     */
    private fun sweepStaleTemps() {
        val parent = path.parent ?: return
        if (!Files.isDirectory(parent)) return
        val live = path.fileName?.toString() ?: return
        Files.newDirectoryStream(parent).use { entries ->
            entries.forEach { candidate ->
                val name = candidate.fileName?.toString() ?: return@forEach
                if (name != live && name.startsWith(live) && name.endsWith(TEMP_SUFFIX)) {
                    try {
                        Files.deleteIfExists(candidate)
                    } catch (_: java.io.IOException) {
                        // Best-effort by design; see KDoc.
                    }
                }
            }
        }
    }

    private fun <T> withFileLock(block: () -> T): T {
        Files.createDirectories(lockFile.parent)
        return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { block() } }
    }

    /**
     * The hard rule: repository/use-case operations return `OperationResult` and never throw to the
     * caller. [SessionMapper] throws on an unreadable value on purpose; this is where that becomes a
     * `Failure` rather than a crash.
     *
     * **[reason] is a fixed token and the exception's own text is never used**, matching
     * `RoomAgentSessionStore.guarded`. kotlinx-serialization appends the offending *input* to a
     * `JsonDecodingException`, so propagating `e.message` would carry the user's raw goal text and
     * workspace paths out inside an [OperationError] — the leak `CommandFailure`'s "display-safe by
     * construction" rule exists to prevent.
     *
     * `Dispatchers.IO` because `channel.lock()` blocks **uninterruptibly**: on `Dispatchers.Default` it
     * would pin one of a small, CPU-sized pool of workers for as long as another process holds the file.
     */
    private suspend fun <T> guarded(reason: String, block: () -> T): OperationResult<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    OperationResult.Success(block())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: CorruptSessionFileException) {
                    OperationResult.Failure(OperationError.UnknownError(reason = CORRUPT))
                } catch (_: Exception) {
                    OperationResult.Failure(OperationError.UnknownError(reason = reason))
                }
            }
        }

    private companion object {

        const val TEMP_SUFFIX = ".tmp"

        const val READ_FAILED = "file_agent_session_read_failed"
        const val WRITE_FAILED = "file_agent_session_write_failed"
        const val DELETE_FAILED = "file_agent_session_delete_failed"
        const val CONSENT_FAILED = "file_agent_consent_write_failed"
        const val CORRUPT = "file_agent_session_corrupt"

        /**
         * One [Mutex] per file, shared by every [JvmAgentSessionStore] addressing it in this JVM.
         *
         * The key is [Path.toAbsolutePath] + [Path.normalize] — a **purely lexical** resolution, chosen
         * over `toRealPath()` deliberately. `toRealPath()` consults the filesystem and throws when the
         * target does not exist, and this store creates its file lazily on the first [write], so at
         * construction there is usually nothing to resolve. Worse, a "try real, fall back to lexical"
         * scheme would key the *same* file differently depending on whether it happened to exist yet,
         * which is the one behaviour a lock key must never have. Lexical normalisation is total and
         * time-invariant: the same path always yields the same gate.
         *
         * The residual is symlink aliasing — two different paths resolving to one file through a link
         * get two gates, and fall back to the `FileLock`, where they collide as before. Naming it rather
         * than implying it is absent: no code constructs this store through a symlink today.
         *
         * Entries are never evicted. Bounded by the number of distinct session paths a process opens,
         * which is one in every use this consumer has.
         */
        private val gates = ConcurrentHashMap<String, Mutex>()

        fun inProcessGate(path: Path): Mutex = gates.computeIfAbsent(path.toString()) { Mutex() }
    }
}
