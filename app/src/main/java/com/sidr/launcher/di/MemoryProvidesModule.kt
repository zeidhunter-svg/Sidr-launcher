package com.sidr.launcher.di

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.ObserveAliasesUseCase
import com.sidr.launcher.domain.memory.alias.PruneUnavailableAliasesUseCase
import com.sidr.launcher.domain.memory.alias.ResolvedCommandStep
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.EvaluateLearnedChoiceDisplayStateUseCase
import com.sidr.launcher.domain.memory.resolution.ObserveLearnedChoicesUseCase
import com.sidr.launcher.domain.memory.resolution.PruneUnavailableLearnedChoicesUseCase
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the Stage-2 S2-1 "Learned Resolutions" use-cases into the graph (Phase C / Task 10).
 * [ResolutionPreferenceStore]'s `@Binds` lives in [MemoryBindsModule] (Hilt forbids mixing
 * `@Provides`/`@Binds` in one module). Graph-only: nothing on the runtime path injects these
 * use-cases yet — Task 11 wires the VM — so behavior is unchanged.
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoryProvidesModule {

    @Provides
    @Singleton
    fun provideResolutionPreferencePolicy(): ResolutionPreferencePolicy =
        DefaultResolutionPreferencePolicy()

    /** Seam over the existing rule-first router so the S2-1 decorator stays trivially testable. */
    @Provides
    @Singleton
    fun provideCommandRouteStep(routeCommandUseCase: RouteCommandUseCase): CommandRouteStep =
        CommandRouteStep { rawInput -> routeCommandUseCase.route(rawInput) }

    @Provides
    @Singleton
    fun provideEvaluateLearnedChoiceDisplayStateUseCase(): EvaluateLearnedChoiceDisplayStateUseCase =
        EvaluateLearnedChoiceDisplayStateUseCase()

    @Provides
    @Singleton
    fun provideResolveCommandWithPreferenceUseCase(
        route: CommandRouteStep,
        store: ResolutionPreferenceStore,
        policy: ResolutionPreferencePolicy,
        catalog: ActionCatalog,
    ): ResolveCommandWithPreferenceUseCase = ResolveCommandWithPreferenceUseCase(
        route = route,
        store = store,
        policy = policy,
        catalog = catalog,
    )

    @Provides
    @Singleton
    fun provideRecordResolutionChoiceUseCase(
        store: ResolutionPreferenceStore,
    ): RecordResolutionChoiceUseCase = RecordResolutionChoiceUseCase(store)

    @Provides
    @Singleton
    fun provideObserveLearnedChoicesUseCase(
        store: ResolutionPreferenceStore,
        installedApps: InstalledAppsRepository,
        displayState: EvaluateLearnedChoiceDisplayStateUseCase,
    ): ObserveLearnedChoicesUseCase = ObserveLearnedChoicesUseCase(
        store = store,
        installedApps = installedApps,
        displayState = displayState,
    )

    @Provides
    @Singleton
    fun provideDeleteLearnedChoiceUseCase(
        store: ResolutionPreferenceStore,
    ): DeleteLearnedChoiceUseCase = DeleteLearnedChoiceUseCase(store)

    @Provides
    @Singleton
    fun providePruneUnavailableLearnedChoicesUseCase(
        store: ResolutionPreferenceStore,
        installedApps: InstalledAppsRepository,
    ): PruneUnavailableLearnedChoicesUseCase = PruneUnavailableLearnedChoicesUseCase(
        store = store,
        installedApps = installedApps,
    )

    @Provides
    @Singleton
    fun provideSaveAliasUseCase(store: AliasStore): SaveAliasUseCase = SaveAliasUseCase(store)

    @Provides
    @Singleton
    fun provideDeleteAliasUseCase(store: AliasStore): DeleteAliasUseCase = DeleteAliasUseCase(store)

    @Provides
    @Singleton
    fun provideObserveAliasesUseCase(
        store: AliasStore,
        installedApps: InstalledAppsRepository,
    ): ObserveAliasesUseCase = ObserveAliasesUseCase(store, installedApps)

    @Provides
    @Singleton
    fun providePruneUnavailableAliasesUseCase(
        store: AliasStore,
        installedApps: InstalledAppsRepository,
    ): PruneUnavailableAliasesUseCase = PruneUnavailableAliasesUseCase(store, installedApps)

    @Provides
    @Singleton
    fun provideResolveCommandWithAliasUseCase(
        inner: ResolveCommandWithPreferenceUseCase,
        store: AliasStore,
        installedApps: InstalledAppsRepository,
    ): ResolveCommandWithAliasUseCase = ResolveCommandWithAliasUseCase(
        inner = ResolvedCommandStep { rawInput -> inner.resolve(rawInput) },
        store = store,
        installedApps = installedApps,
    )
}
