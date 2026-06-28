package com.sidr.launcher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application + WorkManager on-demand initialization (Block Q).
 *
 * Implementing [Configuration.Provider] with the injected [HiltWorkerFactory] lets WorkManager
 * construct `@HiltWorker` workers (the model-download worker) with their Hilt dependencies. The
 * default `androidx.work.WorkManagerInitializer` is removed from the manifest (see AndroidManifest)
 * so this on-demand configuration is the single init path — required by the
 * `RemoveWorkManagerInitializer` lint rule.
 */
@HiltAndroidApp
class SidrLauncherApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
