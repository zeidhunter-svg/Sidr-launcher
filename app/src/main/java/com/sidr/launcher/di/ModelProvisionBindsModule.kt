package com.sidr.launcher.di

import com.sidr.launcher.data.ailocal.provision.ModelDownloadScheduler
import com.sidr.launcher.work.WorkManagerModelDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Block Q — binds the WorkManager-backed scheduler to the `:data:ai-local` [ModelDownloadScheduler]
 * port. The `ModelDownloader` impl lives in `:data:ai-cloud` as a plain class and is `@Provides`-wired
 * in [ModelProvisionProvidesModule], so it needs no `@Binds` here. Keeping the scheduler in `:app`
 * (composition root) means `:data:ai-local` carries no WorkManager edge.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModelProvisionBindsModule {

    @Binds
    @Singleton
    abstract fun bindModelDownloadScheduler(impl: WorkManagerModelDownloadScheduler): ModelDownloadScheduler
}
