package com.sidr.launcher.data.aicloud

import com.sidr.launcher.domain.ai.local.ModelDownloader
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ktor-backed [ModelDownloader] (Block Q, §5.E — reworked: moved out of `:app` into the Phase-5 Ktor
 * module so the real download logic is unit-testable and `:app` keeps only the DI binding).
 *
 * Plain class (no Hilt/`@Inject`) — mirrors `OpenAiCompatibleGenerativeAiEngine`; constructed in
 * `:app` via `@Provides`, reusing the shared cloud `HttpClient`. `:data:ai-cloud` therefore gains no
 * DI dep and `:data:ai-local` gains no HTTP edge (the port lives in `:domain`).
 *
 * **HTTPS-only**: a non-`https://` URL is rejected before any socket opens (permanent error; mirrors
 * the Block-K cloud adapter; cleartext is also forbidden app-wide by `network_security_config.xml`).
 * Streams the body to the quarantine [destination] without buffering the whole artifact. Cancellation
 * propagates ([CancellationException] re-thrown).
 *
 * **Retry taxonomy (P2-7):** 4xx (incl. 404) and non-HTTPS → non-retryable (permanent); 5xx, network,
 * timeout → `NetworkError(retryable = true)` (transient). `ModelProvisioner` maps these to
 * `Result.failure` vs `Result.retry`.
 *
 * Device/release-pending: exercised live only once a real artifact URL is pinned (OQ#2).
 */
class KtorModelDownloader(
    private val httpClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) : ModelDownloader {

    override suspend fun download(url: String, destination: File): OperationResult<Unit> {
        if (!url.startsWith("https://")) {
            // Permanent: a non-HTTPS pin will never succeed; never opens a socket.
            return OperationResult.Failure(OperationError.UnknownError(reason = "model_url_not_https"))
        }
        return withContext(ioDispatcher) {
            try {
                httpClient.prepareGet(url).execute { response ->
                    if (!response.status.isSuccess()) {
                        throw HttpStatusFailure(response.status.value)
                    }
                    destination.outputStream().use { out ->
                        response.bodyAsChannel().toInputStream().use { input -> input.copyTo(out) }
                    }
                }
                OperationResult.Success(Unit)
            } catch (c: CancellationException) {
                throw c
            } catch (e: HttpStatusFailure) {
                // 5xx → transient (server may recover); 4xx → permanent (bad/missing artifact).
                val transient = e.status in 500..599
                OperationResult.Failure(OperationError.NetworkError(retryable = transient))
            } catch (e: Exception) {
                // Connect/read/timeout — transient; WorkManager retries with backoff.
                OperationResult.Failure(OperationError.NetworkError(retryable = true))
            }
        }
    }

    /** Internal carrier so a non-2xx status is mapped to the retry taxonomy after `execute` returns. */
    private class HttpStatusFailure(val status: Int) : Exception()
}
