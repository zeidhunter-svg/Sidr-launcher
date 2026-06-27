package com.sidr.launcher.di

import javax.inject.Qualifier

/**
 * Qualifies the static [com.sidr.launcher.data.repository.ai.StaticFallbackEngine] (Block M).
 *
 * Co-located with [@CloudEngine] so both qualifiers live in the same `:app` DI package. Neither
 * annotation appears in `:data:repository` — [DefaultGenerativeRouter] takes plain constructor
 * params and `:app` supplies the correct engines positionally.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FallbackEngine
