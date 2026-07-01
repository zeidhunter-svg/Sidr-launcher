package com.sidr.launcher.data.ailocal.embedding

/**
 * Provisional ONNX contract for the optional Phase 7 semantic suggestion embedder.
 *
 * OQ#3 is still open, so these names are an inert seam rather than a production promise. The app does
 * not download or run this model until `ModelDownloadConfig.EMBEDDING_PENDING` is replaced with a real
 * URL/hash and the matching vocab/model contract is pinned. Keeping the contract isolated here lets the
 * runtime shell compile without leaking ONNX details into `:domain`.
 */
data class TextEmbeddingModelSpec(
    val inputIdsName: String = "input_ids",
    val attentionMaskName: String = "attention_mask",
    val tokenTypeIdsName: String = "token_type_ids",
    val maxLen: Int = 48,
) {
    init {
        require(maxLen >= 2) { "maxLen must leave room for [CLS]/[SEP]" }
    }

    companion object {
        val DEFAULT = TextEmbeddingModelSpec()
    }
}
