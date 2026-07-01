package com.sidr.launcher.di

import javax.inject.Qualifier

/** Qualifies the inert Phase 7 Block V embedding model config, distinct from the NLU download config. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmbeddingModelConfig
