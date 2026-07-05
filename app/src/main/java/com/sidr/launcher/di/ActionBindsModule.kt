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
 * Nothing injects the catalog on the runtime path yet — the AIL-4 router and AIL-5 confirmation UI
 * consume it — but binding it now validates the graph and keeps the registration additive.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActionBindsModule {

    @Binds
    @Singleton
    abstract fun bindActionCatalog(impl: DefaultActionCatalog): ActionCatalog
}
