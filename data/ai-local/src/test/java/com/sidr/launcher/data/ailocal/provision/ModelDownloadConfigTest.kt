package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.ai.local.ModelId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadConfigTest {

    private val modelId = ModelId("intent-nlu-v1")

    @Test
    fun `both-blank config is inert (not pinned)`() {
        assertFalse(ModelDownloadConfig.INTENT_NLU_PENDING.isPinned)
        assertFalse(ModelDownloadConfig(modelId, url = "", expectedSha256 = "").isPinned)
    }

    @Test
    fun `both-set config is pinned`() {
        assertTrue(ModelDownloadConfig(modelId, "https://host/intent.onnx", "a".repeat(64)).isPinned)
    }

    @Test
    fun `half-pinned config throws at construction (P2-5)`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelDownloadConfig(modelId, url = "https://host/intent.onnx", expectedSha256 = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelDownloadConfig(modelId, url = "", expectedSha256 = "a".repeat(64))
        }
    }
}
