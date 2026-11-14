package com.sidr.launcher.di

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.domain.action.ActionCatalog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [ActionCatalog] port to the AIL-2 [DefaultActionCatalog] (lives in :data:repository).
 * Injected into `SystemIntentToolSource` (the `in_app` tool projection the federation holds) and into
 * the learned-choice decorator (`MemoryProvidesModule`); the AIL-4 router and AIL-5 confirm UI that
 * first consumed it were deleted in A4′ phase 3a.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActionBindsModule {

    @Binds
    @Singleton
    abstract fun bindActionCatalog(impl: DefaultActionCatalog): ActionCatalog
}
