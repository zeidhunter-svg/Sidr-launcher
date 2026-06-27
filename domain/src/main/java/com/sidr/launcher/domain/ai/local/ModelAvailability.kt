package com.sidr.launcher.domain.ai.local

/**
 * Lifecycle state of a local model file.
 *
 * [Available]   — verified on-disk (SHA-256 match confirmed by Block Q).
 * [Missing]     — file absent or explicitly removed.
 * [Unverified]  — file may exist but integrity has not been confirmed; treat the same as absent
 *                 for inference gating (the gate rejects this state — see [LocalInferenceGate]).
 */
enum class ModelAvailability { Available, Missing, Unverified }
