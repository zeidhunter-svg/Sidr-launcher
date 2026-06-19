package com.sidr.launcher.core.common.di

import javax.inject.Qualifier

/** Qualifier for [kotlinx.coroutines.Dispatchers.IO]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Qualifier for [kotlinx.coroutines.Dispatchers.Main]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Qualifier for [kotlinx.coroutines.Dispatchers.Default]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
