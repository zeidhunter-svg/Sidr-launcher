package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.voice.AndroidSpeechInputSource
import com.sidr.launcher.domain.voice.SpeechInputSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Android [SpeechInputSource] implementation (Block T, Fork F7-2). `core/android` keeps
 * no Hilt annotations, so the concrete recognizer is constructed here with the application [Context],
 * mirroring [ConnectivityModule] / [PermissionModule]. A single instance is reused by every call site
 * (the launcher mic is the canonical one; the assistant prompt may reuse the same port).
 *
 * When voice is unavailable on the device [SpeechInputSource.isAvailable] returns false and callers
 * degrade to keyboard input — the launcher core is never blocked.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideSpeechInputSource(
        @ApplicationContext context: Context,
    ): SpeechInputSource = AndroidSpeechInputSource(context)
}
