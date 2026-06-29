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
    init {
        // Fail-fast on a half-pinned config: a `url` without an `expectedSha256` (or vice versa)
        // would let an unverifiable artifact slip past `isPinned` the moment OQ#2 closes. Either
        // both are blank (inert, OQ#2-pending) or both are set (fully pinned) — never one of each.
        require(url.isBlank() == expectedSha256.isBlank()) {
            "Half-pinned ModelDownloadConfig: url and expectedSha256 must both be set or both blank " +
                "(url blank=${url.isBlank()}, sha blank=${expectedSha256.isBlank()})"
        }
    }

    /** True only once a real artifact + its hash are pinned (OQ#2 resolved). */
    val isPinned: Boolean
        get() = url.isNotBlank() && expectedSha256.isNotBlank()

    companion object {
        /**
         * The intent-NLU model (compressed multilingual WordPiece teacher → prune+distill+int8,
         * 7-class, en/ar/tr/ru — see Block P / Open Question #1, amended multilingual 2026-06-29).
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
