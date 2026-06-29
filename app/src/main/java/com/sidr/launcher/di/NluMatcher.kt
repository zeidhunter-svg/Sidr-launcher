package com.sidr.launcher.di

import javax.inject.Qualifier

/**
 * Qualifies the local NLU [com.sidr.launcher.domain.intent.IntentMatcher] (`OnnxIntentClassifier`)
 * — the **secondary** in Block R's `LayeredIntentMatcher`, consulted only when the rule matcher is
 * low-confidence.
 *
 * §5.F decision: the self-gating `OnnxIntentClassifier` is bound here **unconditionally** (not a
 * graph-time real-vs-NoOp swap). It self-gates per inference (LOW_END / no-verified-model /
 * thermal/battery → escape **without** loading ONNX), and model availability flips at runtime when
 * a download completes — a static binding choice would go stale until app restart, so the live
 * per-inference re-check is the more correct design. The same singleton is also exposed as
 * `SessionLifecycle` for the `onTrimMemory` teardown.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NluMatcher
