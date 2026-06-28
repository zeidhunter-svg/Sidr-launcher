package com.sidr.launcher.data.ailocal

import com.sidr.launcher.domain.ai.local.ModelId
import java.io.File
import java.io.InputStream

/**
 * Seam for resolving the on-disk model + tokenizer assets for a [ModelId].
 *
 * Block P consumes verified files; it does NOT download or verify them. Block Q's `ModelStore`
 * implements this (internal storage, quarantine→atomic-rename, SHA-256-verified); the P5
 * `androidTest` supplies a fake pointing at a locally-present model. Both methods return null when
 * the asset is absent, so the classifier degrades to the rule path instead of throwing.
 *
 * Not an `ai.onnxruntime` type.
 */
interface LocalModelFiles {
    /** The verified `.onnx` file, or null if not present/ready. */
    fun modelFile(modelId: ModelId): File?

    /** A stream over the model's `vocab.txt`, or null if not present. Caller closes it. */
    fun vocabStream(modelId: ModelId): InputStream?
}
