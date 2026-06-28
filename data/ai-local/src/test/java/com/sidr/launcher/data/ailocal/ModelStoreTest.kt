package com.sidr.launcher.data.ailocal

import com.sidr.launcher.data.ailocal.provision.Sha256Verifier
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.result.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream

class ModelStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val modelId = ModelId("intent-nlu-v1")
    private val bytes = "the-model-bytes".toByteArray()
    private val hash = Sha256Verifier().sha256Hex(
        java.io.File.createTempFile("hash", ".bin").apply { writeBytes(bytes) }
    )

    private fun store(vocab: ByteArray? = "vocab".toByteArray()): ModelStore =
        ModelStore(
            rootDir = tmp.root,
            vocabOpener = { _ -> vocab?.let { ByteArrayInputStream(it) } },
        )

    @Test
    fun `promote verifies the quarantine file and atomically exposes it as ready`() {
        val store = store()
        store.quarantineFile(modelId).writeBytes(bytes)

        val result = store.promote(modelId, hash)

        assertTrue(result is OperationResult.Success)
        val ready = store.modelFile(modelId)
        assertTrue(ready != null && ready.isFile)
        assertEquals(bytes.toList(), ready!!.readBytes().toList())
        assertFalse("quarantine should be consumed", store.quarantineFile(modelId).exists())
    }

    @Test
    fun `verify-fail deletes quarantine and never exposes a ready file`() {
        val store = store()
        store.quarantineFile(modelId).writeBytes(bytes)

        val result = store.promote(modelId, "0".repeat(64))

        assertTrue(result is OperationResult.Failure)
        assertNull("no unverified file is ever exposed", store.modelFile(modelId))
        assertFalse("corrupt quarantine is deleted", store.quarantineFile(modelId).exists())
    }

    @Test
    fun `modelFile is null when nothing is provisioned`() {
        assertNull(store().modelFile(modelId))
    }

    @Test
    fun `vocabStream returns the asset stream, or null when absent`() {
        store().vocabStream(modelId).use { stream: InputStream? ->
            assertTrue(stream != null)
        }
        assertNull(store(vocab = null).vocabStream(modelId))
    }
}
