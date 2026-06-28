package com.sidr.launcher.data.ailocal.nlu

import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.intent.SimpleCommand
import kotlin.math.exp

/**
 * Pure mapping `logits -> softmax -> argmax -> 7-class label -> IntentMatchResult(source = NLU)`,
 * including the **confidence escape** and the heuristic slot fill. A pure function of a logit
 * array — unit-testable with no `OrtEnvironment`. Deliberately NOT fused into the session/tensor
 * code (P2a/P2b split): this is the part most worth testing and reusing.
 *
 * Confidence escape (mandatory): a fine-tuned 7-class BERT is over-confident on out-of-distribution
 * input (a typo'd app name can emit 0.95 on the wrong class). If argmax == UNKNOWN **or** the
 * max-softmax probability is below [OnnxModelSpec.confidenceFloor], we return a lowest-confidence
 * `UnknownIntent` so Block R's `LayeredIntentMatcher` falls back to the rule path. The softmax
 * confidence is **not calibrated** to the rule-confidence scale (open question for Block R).
 *
 * Pure (no `ai.onnxruntime`, no Android).
 */
class IntentLabelMapper(private val spec: OnnxModelSpec = OnnxModelSpec.DEFAULT) {

    /** Lowest-confidence NLU result → signals "no answer, fall back to rule". */
    fun escape(normalizedInput: String, reason: String): IntentMatchResult = IntentMatchResult(
        normalizedInput = normalizedInput,
        best = IntentCandidate(
            intent = LauncherIntent.UnknownIntent(originalInput = normalizedInput, reason = reason),
            confidence = 0f,
            debugReason = reason,
        ),
        source = MatcherSource.NLU,
        debugReason = reason,
    )

    fun map(normalizedInput: String, logits: FloatArray): IntentMatchResult {
        if (logits.size != spec.labels.size) {
            return escape(normalizedInput, "logit_size_mismatch")
        }
        val probs = softmax(logits)
        var argmax = 0
        for (i in probs.indices) if (probs[i] > probs[argmax]) argmax = i
        val confidence = probs[argmax]
        val label = spec.labels[argmax]

        if (label == NluLabel.UNKNOWN) {
            return escape(normalizedInput, "argmax_unknown")
        }
        if (confidence < spec.confidenceFloor) {
            return escape(normalizedInput, "below_confidence_floor")
        }

        val intent = toIntent(label, normalizedInput)
        return IntentMatchResult(
            normalizedInput = normalizedInput,
            best = IntentCandidate(
                intent = intent,
                confidence = confidence,
                debugReason = "nlu:${label.name}",
            ),
            source = MatcherSource.NLU,
            debugReason = "nlu:${label.name}",
        )
    }

    private fun toIntent(label: NluLabel, normalizedInput: String): LauncherIntent = when (label) {
        NluLabel.LAUNCH_APP ->
            LauncherIntent.LaunchAppIntent(displayNameQuery = SlotExtractor.extractSlot(normalizedInput))
        NluLabel.SEARCH ->
            LauncherIntent.SearchIntent(query = SlotExtractor.extractSlot(normalizedInput), target = SearchTarget.WEB)
        NluLabel.OPEN_SETTINGS -> LauncherIntent.OpenSettingsIntent()
        NluLabel.SHOW_APPS -> LauncherIntent.SimpleCommandIntent(SimpleCommand.SHOW_APPS)
        NluLabel.HELP -> LauncherIntent.SimpleCommandIntent(SimpleCommand.HELP)
        NluLabel.OPEN_ASSISTANT -> LauncherIntent.SimpleCommandIntent(SimpleCommand.OPEN_ASSISTANT)
        // UNKNOWN handled by the escape above; kept exhaustive for safety.
        NluLabel.UNKNOWN -> LauncherIntent.UnknownIntent(originalInput = normalizedInput)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        var sum = 0.0
        val exps = DoubleArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - max).toDouble())
            exps[i] = e
            sum += e
        }
        val out = FloatArray(logits.size)
        for (i in logits.indices) out[i] = (exps[i] / sum).toFloat()
        return out
    }
}
