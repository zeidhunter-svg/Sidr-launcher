package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.prayer.AndroidPrayerLocationProvider
import com.sidr.launcher.data.prayer.AdhanPrayerCalculator
import com.sidr.launcher.data.prayer.BundledCityIndex
import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.prayer.CityIndex
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.PrayerCalculator
import com.sidr.launcher.domain.prayer.PrayerLocationProvider
import com.sidr.launcher.domain.prayer.PrayerPreferencesRepository
import com.sidr.launcher.domain.prayer.PrayerScheduleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Wires the DS-6B prayer stack's concrete collaborators that Hilt cannot auto-construct — plain
 * classes with no `@Inject constructor` ([AdhanPrayerCalculator]), a functional constructor param
 * ([BundledCityIndex]'s `assetOpener`), or a plain-class Android impl built with the application
 * [Context] ([AndroidPrayerLocationProvider], mirroring [PermissionModule]/[VoiceModule]).
 *
 * Split from [PrayerBindsModule] because Hilt forbids mixing `@Provides` (concrete) with `@Binds`
 * (abstract) in one module (the [IntentProvidesModule]/[IntentBindsModule] precedent) —
 * [PrayerPreferencesRepository]/[PrayerScheduleCache]'s `@Inject`-annotated impls are bound there
 * instead.
 *
 * The bundled city index asset (`assets/prayer/cities.gz`, from `:data:prayer`'s own
 * `src/main/assets/`, merged into the app APK via the module dependency) is opened directly through
 * the application [android.content.res.AssetManager] — no Android context ever crosses into
 * `:data:prayer` itself (its `BundledCityIndex` stays context-free by construction, per its kdoc).
 */
@Module
@InstallIn(SingletonComponent::class)
object PrayerProvidesModule {

    private const val CITIES_ASSET_PATH = "prayer/cities.gz"

    @Provides
    @Singleton
    fun providePrayerCalculator(): PrayerCalculator = AdhanPrayerCalculator()

    @Provides
    @Singleton
    fun provideCityIndex(@ApplicationContext context: Context): CityIndex =
        BundledCityIndex(assetOpener = { context.assets.open(CITIES_ASSET_PATH) })

    @Provides
    @Singleton
    fun providePrayerLocationProvider(
        @ApplicationContext context: Context,
        permissionChecker: PermissionChecker,
    ): PrayerLocationProvider = AndroidPrayerLocationProvider(context, permissionChecker)

    /**
     * [Clock.systemDefaultZone] is the single production time/zone source (the use case's own kdoc:
     * "production passes `Clock.systemDefaultZone()`") — VM/test call sites inject their own fixed
     * [Clock] directly, never through this module.
     */
    @Provides
    @Singleton
    fun provideGetPrayerContextUseCase(
        preferences: PrayerPreferencesRepository,
        cache: PrayerScheduleCache,
        calculator: PrayerCalculator,
    ): GetPrayerContextUseCase = GetPrayerContextUseCase(
        preferences = preferences,
        cache = cache,
        calculator = calculator,
        clock = Clock.systemDefaultZone(),
    )
}
