package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.permission.AndroidPermissionChecker
import com.sidr.launcher.domain.permission.PermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Android [PermissionChecker] implementation (Block G). `core/android` keeps no Hilt
 * annotations, so the concrete checker is constructed here with the application [Context].
 * The DataStore-backed `PermissionPrefsRepository` is bound in [PersistenceBindsModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {

    @Provides
    @Singleton
    fun providePermissionChecker(
        @ApplicationContext context: Context,
    ): PermissionChecker = AndroidPermissionChecker(context)
}
