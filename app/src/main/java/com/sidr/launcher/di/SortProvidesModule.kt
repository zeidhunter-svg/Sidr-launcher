package com.sidr.launcher.di

import com.sidr.launcher.data.repository.agent.AppSortResolver
import com.sidr.launcher.domain.sort.ArgSort
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.ClockTimeSort
import com.sidr.launcher.domain.sort.DurationSort
import com.sidr.launcher.domain.sort.SortResolver
import com.sidr.launcher.domain.sort.UrlSort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Android's sort registry (A4′ phase 3b, fork 3b-C; ADR «2026-11-07 — A4′ ф3b: сорт — отдельная ось;
 * F6 закрыт»): one resolver per sort the launcher's catalog (`CatalogArgumentSorts`) declares. The app
 * sort reads this device (`AppSortResolver` over `AppTargetResolver`); the other three are pure
 * functions of the words. `ArgSorts.FILE` is the JVM sandbox's and is not registered here.
 *
 * `SortRegistryGuardTest` (`:app`) calls [provideSortResolvers] itself and fails if a declared sort has
 * no resolver. Injected by no caller yet — the wiring is phase 3b's next tasks; whoever injects it
 * writes `Map<ArgSort, @JvmSuppressWildcards SortResolver>`, since Kotlin emits a wildcard for the
 * value type in parameter position and Dagger would not match it.
 */
@Module
@InstallIn(SingletonComponent::class)
object SortProvidesModule {

    @Provides
    @Singleton
    fun provideSortResolvers(app: AppSortResolver): Map<ArgSort, SortResolver> = mapOf(
        ArgSorts.APP to app,
        ArgSorts.DURATION to DurationSort.resolver,
        ArgSorts.CLOCK_TIME to ClockTimeSort.resolver,
        ArgSorts.URL to UrlSort.resolver,
    )
}
