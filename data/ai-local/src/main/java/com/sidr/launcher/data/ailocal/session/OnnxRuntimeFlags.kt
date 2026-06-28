package com.sidr.launcher.data.ailocal.session

/**
 * Build/profile flags for the ONNX runtime, pinned in one place (Fork P6-5).
 *
 * [NNAPI_ENABLED_BY_DEFAULT] is the production default for the *init-success-but-degraded* failure
 * mode: a session can build with NNAPI yet run slower or return wrong results on some SoCs (the
 * real risk a naive "catch init exception" misses, and NNAPI is deprecated as of Android 15). So
 * NNAPI stays **off by default** and is only enabled per-device after the Block-P device run
 * (SM-A325F + target SoC) proves it **faster AND correct** vs the CPU baseline.
 *
 * The factory takes an explicit override so the P5 `androidTest` can force NNAPI on and compare
 * both paths on one model while the production default stays off.
 */
object OnnxRuntimeFlags {
    const val NNAPI_ENABLED_BY_DEFAULT: Boolean = false

    /** ORT NNAPI EP is only available on API 29+ and is silently ignored on API 28 (Fork P6-5). */
    const val MIN_NNAPI_SDK_INT: Int = 29
}
