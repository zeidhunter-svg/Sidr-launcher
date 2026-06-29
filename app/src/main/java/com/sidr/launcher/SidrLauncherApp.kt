package com.sidr.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.data.ailocal.provision.ModelManager
import com.sidr.launcher.data.ailocal.session.SessionLifecycle
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application: WorkManager on-demand init (Block Q) + the Block R local-NLU lifecycle hooks.
 *
 * Implementing [Configuration.Provider] with the injected [HiltWorkerFactory] lets WorkManager
 * construct `@HiltWorker` workers (the model-download worker) with their Hilt dependencies. The
 * default `androidx.work.WorkManagerInitializer` is removed from the manifest (see AndroidManifest)
 * so this on-demand configuration is the single init path — required by the
 * `RemoveWorkManagerInitializer` lint rule.
 *
 * Block R adds two things, both off the launcher cold/main path:
 *  - **R2.5 — ONNX teardown under memory pressure (Fork P6-9):** [Application] is itself a
 *    [ComponentCallbacks2], so [onTrimMemory]/[onLowMemory] call [SessionLifecycle.releaseResources]
 *    on the bound NLU singleton (the `OnnxIntentClassifier`). `:app` holds only the ONNX-free
 *    [SessionLifecycle] seam — the `ai.onnxruntime` edge never reaches here. The session lazily
 *    re-inits on the next gated inference. §5.D threshold: tear down at [TRIM_MEMORY_BACKGROUND] and
 *    above (and on [onLowMemory]); lighter foreground levels are ignored to avoid thrashing a
 *    session mid-use.
 *  - **R3 — model-provisioning trigger (§5.E):** fire `ModelManager.ensureModel()` once at startup on
 *    the IO-dispatched [ApplicationScope] so it never blocks cold start. Inert while the model is
 *    OQ#2-pending (`config.isPinned == false` → no-op); it also warms Block Q's cached
 *    `DeviceProfile` on first run.
 */
@HiltAndroidApp
class SidrLauncherApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sessionLifecycle: SessionLifecycle

    @Inject
    lateinit var modelManager: ModelManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget; ApplicationScope is SupervisorJob + Dispatchers.IO, so this never touches
        // the main thread and a failure cannot crash startup. No-op until the model is pinned (OQ#2).
        applicationScope.launch { modelManager.ensureModel() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            sessionLifecycle.releaseResources()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        sessionLifecycle.releaseResources()
    }
}
