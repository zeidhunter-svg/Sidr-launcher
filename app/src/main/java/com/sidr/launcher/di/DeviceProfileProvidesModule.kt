package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.device.AndroidDeviceProfiler
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.preferences.DeviceProfileCacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Provides [DeviceProfileProvider] (formerly wired in Block Q's `ModelProvisionProvidesModule`,
 * which was removed in the agentic-restart Этап 0.3 along with the ONNX stack). Consumers today:
 * `SuggestionPrecomputeGate` (LOW_END/battery gating for background suggestion precompute) and
 * `LauncherActivity` (motion-suppression on LOW_END devices).
 */
@Module
@InstallIn(SingletonComponent::class)
object DeviceProfileProvidesModule {

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
}
