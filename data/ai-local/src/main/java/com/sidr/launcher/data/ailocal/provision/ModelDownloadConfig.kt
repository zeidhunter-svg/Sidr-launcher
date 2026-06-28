package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.ai.local.ModelId

/**
 * The single device/release-pending seam for the local-NLU model download (Block Q, §0 / OQ#2).
 *
 * A pinned SHA-256 is meaningless without a pinned artifact, and Phase 5 deliberately chose **no
 * backend** (BYOK). Open Question #2 — model download source / hosting — is **NOT resolved** at
 * Block Q. The entire provisioning mechanism (gate-before-enqueue, verifier, store, idempotency,
 * availability flips, DI, worker shell) is built and JVM-tested against fakes this block; only the
 * live download stays inert until [url] + [expectedSha256] are pinned.
 *
 * [isPinned] is `false` while the placeholders are blank, so [ModelManager] never schedules the
 * worker and [ModelProvisioner] reports `NotConfigured` — the seam is genuinely inert, not pointing
 * at any real host. Tests inject a configured instance (a fake URL + a hash computed over the test
 * bytes) to exercise the full happy path.
 */
data class ModelDownloadConfig(
    val modelId: ModelId,
    /** HTTPS URL of the `.onnx` artifact. Blank until OQ#2 closes. */
    val url: String,
    /** Lowercase hex SHA-256 of the pinned artifact. Blank until OQ#2 closes. */
    val expectedSha256: String,
) {
    /** True only once a real artifact + its hash are pinned (OQ#2 resolved). */
    val isPinned: Boolean
        get() = url.isNotBlank() && expectedSha256.isNotBlank()

    companion object {
        /**
         * The intent-NLU model (BERT-Mini int8, 7-class — see Block P / Open Question #1).
         *
         * TODO(OQ#2): pin the real host [url] + the artifact's [expectedSha256] once model hosting
         * is decided. Until then [isPinned] is false and nothing downloads. The vocab is bundled as
         * an asset (see `ModelStore` / §5.D), so it needs no URL or hash here.
         */
        val INTENT_NLU_PENDING = ModelDownloadConfig(
            modelId = ModelId("intent-nlu-v1"),
            url = "",
            expectedSha256 = "",
        )
    }
}
