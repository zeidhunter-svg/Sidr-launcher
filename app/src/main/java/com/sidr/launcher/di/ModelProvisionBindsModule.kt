package com.sidr.launcher.di

import com.sidr.launcher.data.ailocal.provision.ModelDownloadScheduler
import com.sidr.launcher.data.ailocal.provision.ModelDownloader
import com.sidr.launcher.download.KtorModelDownloader
import com.sidr.launcher.work.WorkManagerModelDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Block Q — binds the `:data:ai-local` provisioning ports to their `:app` implementations. The Ktor
 * downloader and WorkManager scheduler live in `:app` (composition root) so `:data:ai-local` keeps no
 * HTTP / WorkManager edge. Concrete `@Provides` wiring is in [ModelProvisionProvidesModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModelProvisionBindsModule {

    @Binds
    @Singleton
    abstract fun bindModelDownloader(impl: KtorModelDownloader): ModelDownloader

    @Binds
    @Singleton
    abstract fun bindModelDownloadScheduler(impl: WorkManagerModelDownloadScheduler): ModelDownloadScheduler
}
