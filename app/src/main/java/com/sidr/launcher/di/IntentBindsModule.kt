package com.sidr.launcher.di

import com.sidr.launcher.data.repository.intent.AndroidActionExecutor
import com.sidr.launcher.domain.intent.ActionExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [ActionExecutor] port to the Android [AndroidActionExecutor] implementation
 * (lives in :data:repository), mirroring how [RepositoryModule] binds the repository.
 * Interface-to-implementation bindings need `@Binds` in an abstract module; the pure-domain
 * `@Provides` wiring lives in [IntentProvidesModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntentBindsModule {

    @Binds
    @Singleton
    abstract fun bindActionExecutor(impl: AndroidActionExecutor): ActionExecutor
}
