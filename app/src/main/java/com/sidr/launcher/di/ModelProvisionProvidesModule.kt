package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.device.AndroidDeviceProfiler
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.aicloud.KtorModelDownloader
import com.sidr.launcher.data.ailocal.LocalModelFiles
import com.sidr.launcher.data.ailocal.ModelStore
import com.sidr.launcher.data.ailocal.provision.ModelDownloadConfig
import com.sidr.launcher.data.ailocal.provision.ModelDownloadScheduler
import com.sidr.launcher.data.ailocal.provision.ModelManager
import com.sidr.launcher.data.ailocal.provision.ModelProvisioner
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelDownloader
import com.sidr.launcher.domain.ai.local.ModelFilePresence
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.preferences.DeviceProfileCacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.io.InputStream
import javax.inject.Singleton

/**
 * Block Q — produces the local-model provisioning graph (Phase 6). Concrete `@Provides` wiring; the
 * one `@Binds` port→impl binding (`ModelDownloadScheduler`, an `@Inject` class) lives in
 * [ModelProvisionBindsModule] because Hilt forbids mixing `@Provides` and `@Binds` in one module. The
 * [ModelDownloader] impl is a plain class in `:data:ai-cloud` (the Block-K engine pattern), so it is
 * `@Provides`-constructed here with the shared cloud `HttpClient`.
 *
 * Nothing here is on the launcher cold path: the model-availability gate, the device profiler and the
 * download mechanism are only consumed by the (Block R) `OnnxIntentClassifier` and the WorkManager
 * worker. `OnnxIntentClassifier` itself is **not** bound this block — Block R does the
 * `LayeredIntentMatcher` + unqualified-`IntentMatcher` swap.
 */
@Module
@InstallIn(SingletonComponent::class)
object ModelProvisionProvidesModule {

    /** Bundled NLU tokenizer vocab (§5.D): static `assets/nlu/vocab.txt`, not downloaded. */
    private const val NLU_VOCAB_ASSET_PATH = "nlu/vocab.txt"

    /** Future Block V embedding vocab. OQ#3-pending, so absence degrades to heuristic ranking. */
    private const val EMBEDDING_VOCAB_ASSET_PATH = "embedding/vocab.txt"

    @Provides
    @Singleton
    fun provideDeviceProfileProvider(
        @ApplicationContext context: Context,
        cacheRepository: DeviceProfileCacheRepository,
        @ApplicationScope appScope: CoroutineScope,
    ): DeviceProfileProvider = AndroidDeviceProfiler(
        context = context,
        cacheRepository = cacheRepository,
        appScope = appScope,
    )

    @Provides
    @Singleton
    fun provideModelStore(
        @ApplicationContext context: Context,
    ): ModelStore {
        // App-internal, not-backed-up storage: the model is re-downloadable, so it must not bloat
        // cloud backup or restore stale onto a new device.
        val rootDir = context.noBackupFilesDir
        val vocabOpener: (ModelId) -> InputStream? = { modelId ->
            val path = when (modelId) {
                ModelDownloadConfig.INTENT_NLU_PENDING.modelId -> NLU_VOCAB_ASSET_PATH
                ModelDownloadConfig.EMBEDDING_PENDING.modelId -> EMBEDDING_VOCAB_ASSET_PATH
                else -> null
            }
            try {
                path?.let { context.assets.open(it) }
            } catch (e: IOException) {
                null // asset absent (vocab is device/training-pending alongside the model) → degrade
            }
        }
        return ModelStore(rootDir = rootDir, vocabOpener = vocabOpener)
    }

    /** P's `LocalModelFiles` seam is satisfied by the same [ModelStore] instance. */
    @Provides
    @Singleton
    fun provideLocalModelFiles(modelStore: ModelStore): LocalModelFiles = modelStore

    /** Disk-presence cross-check for the availability repo (P1-2) — same [ModelStore] instance. */
    @Provides
    @Singleton
    fun provideModelFilePresence(modelStore: ModelStore): ModelFilePresence = modelStore

    /**
     * The Ktor [ModelDownloader] impl lives in `:data:ai-cloud` (HTTP module) as a plain class —
     * constructed here with the shared cloud [HttpClient] (provided in `AiCloudProvidesModule`).
     */
    @Provides
    @Singleton
    fun provideModelDownloader(
        httpClient: HttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): ModelDownloader = KtorModelDownloader(httpClient = httpClient, ioDispatcher = ioDispatcher)

    @Provides
    @Singleton
    fun provideModelDownloadConfig(): ModelDownloadConfig = ModelDownloadConfig.INTENT_NLU_PENDING

    @Provides
    @Singleton
    @EmbeddingModelConfig
    fun provideEmbeddingModelDownloadConfig(): ModelDownloadConfig = ModelDownloadConfig.EMBEDDING_PENDING

    @Provides
    @Singleton
    fun provideModelProvisioner(
        modelStore: ModelStore,
        downloader: ModelDownloader,
        availabilityRepository: ModelAvailabilityRepository,
        config: ModelDownloadConfig,
    ): ModelProvisioner = ModelProvisioner(
        store = modelStore,
        downloader = downloader,
        availability = availabilityRepository,
        config = config,
    )

    @Provides
    @Singleton
    fun provideModelManager(
        deviceProfileProvider: DeviceProfileProvider,
        availabilityRepository: ModelAvailabilityRepository,
        scheduler: ModelDownloadScheduler,
        config: ModelDownloadConfig,
    ): ModelManager = ModelManager(
        deviceProfileProvider = deviceProfileProvider,
        availability = availabilityRepository,
        scheduler = scheduler,
        config = config,
    )
}
