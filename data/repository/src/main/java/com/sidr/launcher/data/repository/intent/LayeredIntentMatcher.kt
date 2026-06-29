package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource

/**
 * Block R / Fork P6-2 — composite [IntentMatcher] that layers a local NLU matcher **behind** the
 * rule matcher. Mirrors how `DefaultGenerativeRouter` composes engines behind the
 * `GenerativeAiEngine` port: it references only the [IntentMatcher] **port** for both
 * collaborators, so there is **no `data→data` edge** (it never imports `RuleBasedIntentMatcher` or
 * the ONNX classifier — both are injected as the port from `:app`).
 *
 * Precedence — **RULE-FIRST** (§5.B, decided; do not invert):
 *  1. `rule = primary.match(input)`.
 *  2. **Fast path:** if the rule is **not** low-confidence, return it **verbatim** and **never call
 *     the secondary** — this preserves the `< 10ms` rule path and exact parity when no model is
 *     present (the classifier escapes, but it is never even consulted here).
 *  3. **Low-confidence branch only:** consult `secondary.match(input)`.
 *     - If the NLU result is an **escape** (its "no answer" signal — see [isEscape]), the weak
 *       `rule` stands.
 *     - Otherwise the NLU answer wins (`source = NLU`) iff its **calibrated** confidence clears the
 *       suggest threshold; the calibration (§5.A) maps it into the suggest band so it Suggests but
 *       never auto-executes. Else the rule stands.
 *
 * Two-port invariant intact: this is an [IntentMatcher]; it never touches generation, and
 * `MatcherSource.AI` stays absent. One confidence policy: the calibration lives in the
 * [calibrator] this class owns — no second [IntentConfidencePolicy].
 */
class LayeredIntentMatcher(
    private val primary: IntentMatcher,
    private val secondary: IntentMatcher,
    private val policy: IntentConfidencePolicy = DefaultIntentConfidencePolicy(),
    private val calibrator: NluConfidenceCalibrator = NluConfidenceCalibrator(policy),
) : IntentMatcher {

    override suspend fun match(normalizedInput: String): IntentMatchResult {
        val rule = primary.match(normalizedInput)

        // Fast path: a confident deterministic rule wins verbatim; NLU is never consulted.
        if (!policy.isLowConfidence(rule.best.confidence)) return rule

        // Low-confidence (ambiguous) input only: consult the NLU secondary.
        val nlu = secondary.match(normalizedInput)
        if (isEscape(nlu)) return rule

        val calibrated = calibrator.calibrate(nlu.best.confidence)
        if (!policy.shouldSuggest(calibrated)) return rule

        return nlu.copy(
            best = nlu.best.copy(confidence = calibrated),
            source = MatcherSource.NLU,
        )
    }

    /**
     * The NLU "no answer, fall back to rule" signal, pinned **structurally** (not by free-text
     * `debugReason`). Every escape the Block-P classifier emits — `gate_off`, `inference_error`,
     * `argmax_unknown`, `below_confidence_floor`, `logit_size_mismatch` — goes through
     * `IntentLabelMapper.escape`, producing `source = NLU`, `confidence = 0f`, and an
     * `UnknownIntent`. We match any of those structural markers.
     */
    private fun isEscape(result: IntentMatchResult): Boolean =
        result.source == MatcherSource.NLU &&
            (result.best.confidence == 0f || result.best.intent is LauncherIntent.UnknownIntent)
}
