package com.sidr.launcher.domain.ai.local

/**
 * Narrow port reporting whether a [ModelId]'s **verified** artifact is physically present on disk
 * (Block Q, P1-2). Implemented by `ModelStore` in `:data:ai-local` (a verified file only ever reaches
 * the ready path after SHA-256 verification + atomic rename).
 *
 * Exists so `ModelAvailabilityRepository` implementations (`:data:repository`) can cross-check disk
 * truth against the persisted availability marker **without** a `data→data` edge — both depend on this
 * domain port. A boolean only (no `java.io.File`), keeping the integrity surface minimal: presence is
 * the disk fact; verification provenance is the marker's job.
 */
interface ModelFilePresence {
    /** True iff a verified, ready-to-load model file exists on disk for [modelId]. */
    fun isModelPresent(modelId: ModelId): Boolean
}
