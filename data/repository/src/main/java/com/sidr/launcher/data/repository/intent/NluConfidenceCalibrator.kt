package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.IntentConfidencePolicy

/**
 * Block R / §5.A — maps a winning NLU result's raw softmax confidence onto the
 * [IntentConfidencePolicy] scale. Pure; owned by [LayeredIntentMatcher]; **not** a second
 * [IntentConfidencePolicy].
 *
 * Why this exists: the local NLU classifier (Block P) emits raw softmax confidence in
 * `[confidenceFloor, 1.0]` (anything below the floor already escapes inside the classifier). A
 * fine-tuned 7-class BERT is **over-confident** and its softmax is **not a calibrated probability**,
 * yet the surfaced confidence drives whether `HandleUserCommandUseCase` auto-executes a
 * side-effecting launcher action. NLU is consulted **only on ambiguous input** (the rule was
 * already low-confidence — §5.B rule-first), so the conservative, decided mapping is:
 *
 *   raw ∈ [nluConfidenceFloor, 1.0]  →  [suggestThreshold, autoExecuteThreshold)
 *
 * i.e. a winning NLU answer **always Suggests** (the user confirms) and **never silently
 * auto-executes**. The deterministic rule fast path (§5.B step 2) still lets a *confident* rule
 * short-circuit before NLU is ever consulted, so explicit commands are untouched.
 *
 * Decision on `confidenceFloor` home (R1 open point): it **stays in `OnnxModelSpec`** (it governs
 * the escape *inside* the classifier). This calibrator takes its input-domain floor as a plain
 * [Float] (default mirrors `OnnxModelSpec.confidenceFloor = 0.60f`) so `:data:repository` keeps
 * **no edge to `:data:ai-local`**.
 */
class NluConfidenceCalibrator(
    private val suggestThreshold: Float,
    private val autoExecuteThreshold: Float,
    private val nluConfidenceFloor: Float = DEFAULT_NLU_CONFIDENCE_FLOOR,
) {

    /** Convenience: derive the band endpoints from the single confidence [policy]. */
    constructor(
        policy: IntentConfidencePolicy,
        nluConfidenceFloor: Float = DEFAULT_NLU_CONFIDENCE_FLOOR,
    ) : this(policy.suggestThreshold, policy.autoExecuteThreshold, nluConfidenceFloor)

    init {
        require(suggestThreshold < autoExecuteThreshold) {
            "suggestThreshold ($suggestThreshold) must be < autoExecuteThreshold ($autoExecuteThreshold)"
        }
        require(nluConfidenceFloor in 0f..1f) {
            "nluConfidenceFloor must be in [0,1], was $nluConfidenceFloor"
        }
    }

    /**
     * Linear remap of [rawNluConfidence] (expected `[nluConfidenceFloor, 1.0]`, clamped defensively)
     * into the suggest band. The result is **guaranteed strictly below** [autoExecuteThreshold]
     * (kept [AUTO_EXECUTE_MARGIN] under it), so a model-driven intent can never auto-execute.
     */
    fun calibrate(rawNluConfidence: Float): Float {
        val clamped = rawNluConfidence.coerceIn(nluConfidenceFloor, 1.0f)
        val span = 1.0f - nluConfidenceFloor
        val frac = if (span <= 0f) 0f else (clamped - nluConfidenceFloor) / span
        val mapped = suggestThreshold + frac * (autoExecuteThreshold - suggestThreshold)
        return mapped.coerceAtMost(autoExecuteThreshold - AUTO_EXECUTE_MARGIN)
    }

    companion object {
        /** Mirrors `OnnxModelSpec.confidenceFloor` without importing it (no `data→data` edge). */
        const val DEFAULT_NLU_CONFIDENCE_FLOOR = 0.60f

        /** Keeps a calibrated NLU confidence strictly below the auto-execute threshold. */
        const val AUTO_EXECUTE_MARGIN = 0.01f
    }
}
