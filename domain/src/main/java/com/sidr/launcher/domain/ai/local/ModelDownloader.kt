package com.sidr.launcher.domain.ai.local

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.io.File

/**
 * Port for fetching a model artifact to a local file (Phase 6, Block Q).
 *
 * Lives in `:domain` (stdlib-only signature — `java.io.File` is JVM stdlib) so the implementation can
 * live in the Phase-5 Ktor module (`:data:ai-cloud`) while the consumer (`ModelProvisioner` in
 * `:data:ai-local`) depends only on this port — **no `data→data` edge**, no HTTP edge into
 * `:data:ai-local`. `:app` holds only the DI binding.
 *
 * Returns [OperationResult] and never throws an expected error. **Failure classification matters for
 * the worker's retry taxonomy:**
 *  - **transient** (network down / 5xx / timeout) → [OperationError.NetworkError] with
 *    `retryable = true` → the worker retries with backoff.
 *  - **permanent** (4xx incl. 404, non-HTTPS URL, malformed) → any non-retryable error → the worker
 *    fails (re-fetching the same pinned artifact will not fix it).
 *
 * [kotlinx.coroutines.CancellationException] MUST be re-thrown by implementations so a stopped worker
 * aborts mid-download cooperatively.
 */
interface ModelDownloader {
    /**
     * Downloads [url] into [destination] (a quarantine file). On success the bytes are fully written;
     * on failure the destination may be partial — the caller discards it and never promotes it.
     * Must use HTTPS only.
     */
    suspend fun download(url: String, destination: File): OperationResult<Unit>
}
