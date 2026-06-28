package com.sidr.launcher.data.aicloud

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class KtorModelDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dest() = File(tmp.root, "intent.onnx.tmp")

    private fun downloader(
        requests: AtomicInteger,
        handler: () -> Pair<HttpStatusCode, ByteArray>,
    ): KtorModelDownloader {
        val engine = MockEngine {
            requests.incrementAndGet()
            val (status, body) = handler()
            respond(content = ByteReadChannel(body), status = status)
        }
        return KtorModelDownloader(HttpClient(engine), UnconfinedTestDispatcher())
    }

    @Test
    fun `non-https URL is rejected before any socket opens`() = runTest {
        val requests = AtomicInteger(0)
        val dl = downloader(requests) { HttpStatusCode.OK to ByteArray(0) }

        val result = dl.download("http://insecure.example/intent.onnx", dest())

        assertTrue(result is OperationResult.Failure)
        assertEquals(0, requests.get()) // no request issued
        assertFalse(dest().exists())
    }

    @Test
    fun `https 200 streams the body to the destination`() = runTest {
        val bytes = "the-model-bytes".toByteArray()
        val dl = downloader(AtomicInteger(0)) { HttpStatusCode.OK to bytes }

        val result = dl.download("https://host.example/intent.onnx", dest())

        assertTrue(result is OperationResult.Success)
        assertEquals(bytes.toList(), dest().readBytes().toList())
    }

    @Test
    fun `4xx is a permanent (non-retryable) failure`() = runTest {
        val dl = downloader(AtomicInteger(0)) { HttpStatusCode.NotFound to ByteArray(0) }

        val result = dl.download("https://host.example/missing.onnx", dest())

        val error = (result as OperationResult.Failure).error
        assertTrue(error is OperationError.NetworkError)
        assertFalse((error as OperationError.NetworkError).retryable)
    }

    @Test
    fun `5xx is a transient (retryable) failure`() = runTest {
        val dl = downloader(AtomicInteger(0)) { HttpStatusCode.ServiceUnavailable to ByteArray(0) }

        val result = dl.download("https://host.example/intent.onnx", dest())

        val error = (result as OperationResult.Failure).error
        assertTrue(error is OperationError.NetworkError)
        assertTrue((error as OperationError.NetworkError).retryable)
    }
}
