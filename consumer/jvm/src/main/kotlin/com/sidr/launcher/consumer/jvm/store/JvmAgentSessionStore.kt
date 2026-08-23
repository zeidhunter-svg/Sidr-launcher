package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * The second consumer's [AgentSessionStore]: one JSON file holding at most one session.
 *
 * **No history at rest.** A terminal state deletes the file, exactly as the Room store deletes by
 * cascade — a durable trace journal is A5's decision, not one A0.5 quietly introduces.
 *
 * **[recordConsentIfPending] is a real compare-and-set**, and that is the point of this class. The
 * contract exists because Room can say `UPDATE … WHERE state = pending` in one statement; honouring it
 * here without weakening it to a whole-object write is what decides whether `AgentSessionStore` is a
 * portable port or a Room shape wearing an interface (spec §8, §11.2). Two mechanisms, because they
 * exclude different things: a [Mutex] for coroutines inside this process, a [java.nio.channels.FileLock]
 * for a second process opening the same file.
 *
 * Whole-object writes were avoided deliberately: that pattern already cost this project the
 * `autoHideNavBar` bug (DS-11), which only surfaced on device.
 */
class JvmAgentSessionStore(private val file: Path) : AgentSessionStore {

    private val mutex = Mutex()
    private val lockFile: Path = file.resolveSibling(file.fileName.toString() + ".lock")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    override suspend fun active(): OperationResult<AgentSession?> = mutex.withLock {
        guarded { withFileLock { read() } }
    }

    override suspend fun save(session: AgentSession): OperationResult<Unit> = mutex.withLock {
        guarded { withFileLock { write(session) } }
    }

    override suspend fun delete(id: AgentSessionId): OperationResult<Unit> = mutex.withLock {
        guarded {
            withFileLock {
                if (read()?.id == id) {
                    Files.deleteIfExists(file)
                }
            }
        }
    }

    override suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean> = mutex.withLock {
        guarded {
            withFileLock {
                val current = read()
                when {
                    current == null -> false
                    current.id != id -> false
                    current.state != ExecutionState.AwaitingConsent -> false
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
    }

    private fun read(): AgentSession? {
        if (!Files.exists(file)) return null
        val text = Files.readString(file)
        if (text.isBlank()) return null
        return SessionMapper.fromDto(json.decodeFromString(SessionDto.serializer(), text))
    }

    private fun write(session: AgentSession) {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            json.encodeToString(SessionDto.serializer(), SessionMapper.toDto(session)),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
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
     */
    private inline fun <T> guarded(block: () -> T): OperationResult<T> =
        runCatching(block).fold(
            onSuccess = { OperationResult.Success(it) },
            onFailure = { OperationResult.Failure(OperationError.UnknownError(it.message)) },
        )
}
