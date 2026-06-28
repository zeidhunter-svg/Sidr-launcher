package com.sidr.launcher.data.ailocal.session

/**
 * Narrow seam for releasing native ONNX resources under memory pressure (Fork P6-9 / P3).
 *
 * `:app` registers a `ComponentCallbacks2.onTrimMemory(...)` hook that calls [releaseResources]
 * on the bound matcher — so `:app` depends on this small port, never on the ONNX impl directly,
 * and `ComponentCallbacks2` never leaks into `:domain`. Implemented by `OnnxIntentClassifier`.
 *
 * Distinct from a *transient* gate-off (thermal/battery flicker), which only skips one inference
 * and keeps the session — see `OnnxIntentClassifier`. [releaseResources] is the heavy teardown:
 * the session lazily re-inits on the next gated inference.
 *
 * Not an `ai.onnxruntime` type — keep this interface ONNX-free so `:app` can hold it without the
 * runtime edge.
 */
interface SessionLifecycle {
    /** Closes any open native session and frees its memory. Safe to call when nothing is open. */
    fun releaseResources()
}
