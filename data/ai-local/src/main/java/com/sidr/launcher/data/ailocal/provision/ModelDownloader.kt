package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.result.OperationResult
import java.io.File

/**
 * Thin download port (Block Q, §5.E). `:data:ai-local` may only depend on
 * `:domain, :core:common, :core:android, ONNX` (architecture.md) — it has **no** HTTP edge — so the
 * worker/provisioner depend on this interface and stay HTTP-free and JVM-testable with a
 * `FakeModelDownloader`. The real Ktor-backed impl lives in `:app` (the composition root, where the
 * cloud-AI `HttpClient` already lives), keeping any HTTP dependency out of this module and avoiding a
 * data→data edge.
 *
 * Returns [OperationResult] and never throws an expected error. [CancellationException] MUST be
 * re-thrown by implementations so a stopped worker cooperatively aborts mid-download.
 */
interface ModelDownloader {
    /**
     * Downloads [url] into [destination] (a quarantine file). On success the bytes are fully written;
     * on failure the destination may be partial — the caller (quarantine) is discarded and never
     * promoted. Must use HTTPS only.
     */
    suspend fun download(url: String, destination: File): OperationResult<Unit>
}
