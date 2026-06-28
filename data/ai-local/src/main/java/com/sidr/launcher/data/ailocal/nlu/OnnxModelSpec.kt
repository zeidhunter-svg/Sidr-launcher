package com.sidr.launcher.data.ailocal.nlu

/**
 * The pinned input/output contract for the local NLU ONNX model (Open Question #1, 2026-06-28).
 *
 * P0 pins these; P2 builds exactly against them. A mismatch between this spec and the actual
 * `.onnx` (a wrong input name, a missing input, a wrong sequence length) makes `session.run`
 * throw → silent permanent rule-fallback. The placeholder model in `tools/nlu/` is authored to
 * these exact names/dtypes/axes so swapping in the real model needs no Kotlin change.
 *
 * Sequence axis is **fixed** at [maxLen]; the tokenizer always pads/truncates to exactly
 * `[1, maxLen]`, which is valid for both a fixed-`maxLen` export and a dynamic one.
 *
 * [confidenceFloor] is the **confidence escape** threshold (P2a): a fine-tuned 7-class BERT is
 * over-confident on out-of-distribution input, so a max-softmax below this floor (or an argmax of
 * [NluLabel.UNKNOWN]) is treated as "no answer" and returned at lowest confidence, letting the
 * Block-R `LayeredIntentMatcher` fall back to the rule path. This softmax confidence is **NOT
 * calibrated** against the rule-matcher confidence scale — the merge policy is an open question
 * for Block R.
 *
 * Pure (no `ai.onnxruntime`, no Android).
 */
data class OnnxModelSpec(
    val inputIdsName: String = "input_ids",
    val attentionMaskName: String = "attention_mask",
    val tokenTypeIdsName: String = "token_type_ids",
    val outputLogitsName: String = "logits",
    val maxLen: Int = 32,
    val confidenceFloor: Float = 0.60f,
    val labels: List<NluLabel> = NluLabel.entries,
) {
    init {
        require(maxLen >= 2) { "maxLen must leave room for [CLS]/[SEP]" }
        require(labels.isNotEmpty()) { "labels must be non-empty" }
    }

    companion object {
        val DEFAULT = OnnxModelSpec()
    }
}
