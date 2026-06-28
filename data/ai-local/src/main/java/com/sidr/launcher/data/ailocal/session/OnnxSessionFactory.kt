package com.sidr.launcher.data.ailocal.session

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.os.Build
import android.util.Log
import java.util.EnumSet

/**
 * Builds an [OrtSession] with the **deterministic CPU EP as the default** and an **opportunistic
 * NNAPI EP** appended only when API ≥ 29 and the NNAPI flag is on (Fork P6-5).
 *
 * CPU is always retained — ORT's CPU EP is the last fallback in the ordered provider list, so a
 * session is correct and usable on CPU alone on every supported device; the `<150ms` MID_RANGE
 * budget is measured on the CPU path.
 *
 * Both Fork-P6-5 failure modes are handled:
 *  - (a) **init failure** — `addNnapi`/`createSession` throws → catch, rebuild a CPU-only session,
 *    log a non-PII warning, never crash.
 *  - (b) **init-success-but-degraded** — NNAPI is **off by default** ([OnnxRuntimeFlags]); it is
 *    only turned on per-device after the P5 device run proves it faster AND correct vs CPU.
 *
 * [nnapiEnabled] and [sdkInt] are constructor seams so the P5 `androidTest` can force NNAPI on to
 * compare both paths on one model while production stays off.
 *
 * No state, no caching of the session here — the single shared session lives in
 * `OnnxIntentClassifier`.
 */
class OnnxSessionFactory(
    private val nnapiEnabled: Boolean = OnnxRuntimeFlags.NNAPI_ENABLED_BY_DEFAULT,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    /** A created session plus whether NNAPI actually went on (for P5 path comparison / logging). */
    data class Created(val session: OrtSession, val nnapiActive: Boolean)

    /**
     * @throws OrtException only if even a plain CPU session cannot be created (caller degrades).
     */
    fun create(env: OrtEnvironment, modelBytes: ByteArray): Created {
        if (nnapiEnabled && sdkInt >= OnnxRuntimeFlags.MIN_NNAPI_SDK_INT) {
            try {
                val opts = OrtSession.SessionOptions()
                // Empty flag set → NNAPI with CPU fallback for unsupported ops retained.
                opts.addNnapi(EnumSet.noneOf(NNAPIFlags::class.java))
                val session = env.createSession(modelBytes, opts)
                Log.i(TAG, "ONNX session created with NNAPI EP (CPU fallback retained).")
                return Created(session, nnapiActive = true)
            } catch (e: OrtException) {
                // (a) init failure → fall through to CPU-only. Reason only, no model/user data.
                Log.w(TAG, "NNAPI EP init failed (${e.code}); falling back to CPU-only session.")
            } catch (e: RuntimeException) {
                Log.w(TAG, "NNAPI EP init error (${e.javaClass.simpleName}); falling back to CPU-only.")
            }
        }
        val opts = OrtSession.SessionOptions()
        return Created(env.createSession(modelBytes, opts), nnapiActive = false)
    }

    private companion object {
        const val TAG = "OnnxSessionFactory"
    }
}
