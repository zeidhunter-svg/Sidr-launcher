package com.sidr.launcher.di

import com.sidr.launcher.data.repository.db.ResolutionPreferenceStoreImpl
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Stage-2 S2-1 [ResolutionPreferenceStore] port to its Room-backed impl (Phase B / Task 8).
 * Kept separate from [MemoryProvidesModule] because Hilt forbids mixing `@Provides` and `@Binds` in
 * one module (see the `DatabaseModule`/`HistoryBindsModule` precedent). Nothing on the runtime path
 * injects the S2-1 use-cases yet — Task 11 wires the VM.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryBindsModule {

    @Binds
    @Singleton
    abstract fun bindResolutionPreferenceStore(
        impl: ResolutionPreferenceStoreImpl,
    ): ResolutionPreferenceStore
}
