package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.connectivity.AndroidConnectivityChecker
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Android [ConnectivityChecker] implementation (Block M, Fork P5-7). `core/android`
 * keeps no Hilt annotations, so the concrete checker is constructed here with the application
 * [Context], mirroring the [PermissionModule] precedent.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConnectivityModule {

    @Provides
    @Singleton
    fun provideConnectivityChecker(
        @ApplicationContext context: Context,
    ): ConnectivityChecker = AndroidConnectivityChecker(context)
}
