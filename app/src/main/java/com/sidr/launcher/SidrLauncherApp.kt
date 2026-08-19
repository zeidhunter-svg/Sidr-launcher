package com.sidr.launcher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.work.SuggestionsWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application: WorkManager on-demand init for the periodic suggestion precompute/usage-cleanup
 * workers (Phase 7, Block W).
 *
 * Implementing [Configuration.Provider] with the injected [HiltWorkerFactory] lets WorkManager
 * construct `@HiltWorker` workers with their Hilt dependencies. The default
 * `androidx.work.WorkManagerInitializer` is removed from the manifest (see AndroidManifest) so this
 * on-demand configuration is the single init path — required by the `RemoveWorkManagerInitializer`
 * lint rule.
 */
@HiltAndroidApp
class SidrLauncherApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var suggestionsWorkScheduler: SuggestionsWorkScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { suggestionsWorkScheduler.ensureScheduled() }
    }
}
