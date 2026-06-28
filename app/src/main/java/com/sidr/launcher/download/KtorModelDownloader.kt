package com.sidr.launcher.download

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.ailocal.provision.ModelDownloader
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
import java.io.IOException
import javax.inject.Inject

/**
 * Ktor-backed [ModelDownloader] (Block Q, §5.E). Lives in `:app` — the composition root, where the
 * cloud-AI [HttpClient] already lives — so `:data:ai-local` keeps no HTTP edge (and no data→data
 * edge is introduced). Reuses the same lazily-built `HttpClient`; nothing on the launcher cold path
 * injects it.
 *
 * **HTTPS-only**: a non-`https://` URL is rejected before any socket opens (mirrors the Block-K cloud
 * adapter; cleartext is also forbidden app-wide by `network_security_config.xml`). Streams the body
 * to the quarantine [destination] without buffering the whole artifact in memory. Cancellation
 * (a stopped worker) propagates: [CancellationException] is re-thrown.
 *
 * Device/release-pending: exercised live only once a real artifact URL is pinned (OQ#2).
 */
class KtorModelDownloader @Inject constructor(
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelDownloader {

    override suspend fun download(url: String, destination: File): OperationResult<Unit> {
        if (!url.startsWith("https://")) {
            return OperationResult.Failure(OperationError.UnknownError(reason = "model_url_not_https"))
        }
        return withContext(ioDispatcher) {
            try {
                httpClient.prepareGet(url).execute { response ->
                    if (!response.status.isSuccess()) {
                        throw IOException("model_download_http_${response.status.value}")
                    }
                    destination.outputStream().use { out ->
                        response.bodyAsChannel().toInputStream().use { input -> input.copyTo(out) }
                    }
                }
                OperationResult.Success(Unit)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                OperationResult.Failure(OperationError.NetworkError())
            }
        }
    }
}
