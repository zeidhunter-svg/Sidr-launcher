package com.sidr.launcher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutRefreshTrigger
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
 *
 * **A1″ adds the second app-scoped startup job**, [ShortcutRefreshTrigger]. That is a reuse of this
 * existing owner rather than a new one: the app list is refreshed by `LauncherAppList` from
 * `LauncherViewModel`'s `init` — a feature-layer object with no `Context` and no package observer — and
 * nothing in this repository watched package or shortcut changes before now. See that trigger's KDoc
 * for the reading of the tree behind the choice, and for what it does on a device where Sidr is not the
 * home app.
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

    // A1″ Task 7: the `app_shortcut` adapter's snapshot. Costs one coroutine launch here — the
    // LauncherApps registration and every refresh happen on [applicationScope], which the graph builds
    // over the IO dispatcher, so nothing on this path touches the main thread or the network.
    @Inject
    lateinit var shortcutRefreshTrigger: ShortcutRefreshTrigger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { suggestionsWorkScheduler.ensureScheduled() }
        shortcutRefreshTrigger.start(applicationScope)
    }
}
