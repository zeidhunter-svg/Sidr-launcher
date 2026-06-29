package com.sidr.launcher.data.ailocal.nlu

/**
 * The pinned input/output contract for the local NLU ONNX model (Open Question #1, resolved
 * 2026-06-28; **amended multilingual 2026-06-29** — see decisions.md "ADR — OQ#1 amended").
 *
 * P0 pins these; P2 builds exactly against them. A mismatch between this spec and the actual
 * `.onnx` (a wrong input name, a missing input, a wrong sequence length) makes `session.run`
 * throw → silent permanent rule-fallback. The placeholder model in `tools/nlu/` is authored to
 * these exact names/dtypes/axes so swapping in the real model needs no Kotlin change.
 *
 * **Multilingual amendment (en/ar/tr/ru):** the shipped model is produced by compressing a
 * multilingual WordPiece teacher (`bert-base-multilingual-uncased`) — **vocab-pruned** to the four
 * target languages + the launcher command domain (~110k → ~20–30k rows), **layer-distilled** to a
 * small student, then **int8**. This is Stage-2 work; the only contract change here is the pinned
 * constants below. The 7 labels are language-independent and do NOT change.
 *
 * Sequence axis is **fixed** at [maxLen]; the tokenizer always pads/truncates to exactly
 * `[1, maxLen]`, which is valid for both a fixed-`maxLen` export and a dynamic one. [maxLen] is
 * **48** (provisional, finalized at dataset time): Turkish (agglutinative) and Arabic fragment
 * into more wordpieces per word than English, so the old 32 was too tight.
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
    val maxLen: Int = 48,
    val confidenceFloor: Float = 0.60f,
    /**
     * Row count of the model's token-embedding table = line count of the shipped `vocab.txt`.
     * **Provisional / data-driven:** the pruned multilingual `vocab.txt` does not exist yet
     * (Stage 2 / OQ#1), so this is the [VOCAB_SIZE_PENDING] sentinel — NOT a guessed literal and
     * NOT the old English 30522. At Stage 2 set it to the actual pruned line count (~20–30k for
     * en/ar/tr/ru). `tools/nlu/train_export.py:assert_onnx_contract` derives the same number from
     * the produced `vocab.txt` and hard-fails the export if the model's embedding rows disagree —
     * keep this constant hand-synced to that value (the Phase-4 `TABLE_NAMES` precedent).
     */
    val vocabSize: Int = VOCAB_SIZE_PENDING,
    val labels: List<NluLabel> = NluLabel.entries,
) {
    init {
        require(maxLen >= 2) { "maxLen must leave room for [CLS]/[SEP]" }
        require(labels.isNotEmpty()) { "labels must be non-empty" }
        require(vocabSize == VOCAB_SIZE_PENDING || vocabSize > 0) {
            "vocabSize must be the pending sentinel or a positive vocab line count"
        }
    }

    companion object {
        /** Sentinel: the pruned multilingual vocab size is not yet known (Stage 2 / OQ#1). */
        const val VOCAB_SIZE_PENDING = -1

        val DEFAULT = OnnxModelSpec()
    }
}
