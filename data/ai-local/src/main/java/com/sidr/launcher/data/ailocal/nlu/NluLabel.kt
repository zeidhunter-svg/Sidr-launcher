package com.sidr.launcher.data.ailocal.nlu

/**
 * The 7 sequence-classification labels the local NLU model emits, in **argmax-index order**.
 *
 * This ordinal order IS the contract with the trained model (Open Question #1, resolved
 * 2026-06-28): the model's output `logits[i]` corresponds to `entries[i]`. It MUST match the
 * label order used when fine-tuning + exporting the ONNX model (see `tools/nlu/train_export.py`).
 * Changing this order without re-training silently mislabels every inference.
 *
 * `CLEAR` is intentionally **not** a class — "clear input" is a fixed UI phrase the rule matcher
 * already nails, so it stays rule-only (see the plan, Open Question #1).
 *
 * Pure (no `ai.onnxruntime`, no Android): part of the P2a JVM-testable layer.
 */
enum class NluLabel {
    LAUNCH_APP,
    SEARCH,
    OPEN_SETTINGS,
    SHOW_APPS,
    HELP,
    OPEN_ASSISTANT,
    UNKNOWN,
}
