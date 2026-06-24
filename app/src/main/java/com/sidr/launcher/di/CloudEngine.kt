package com.sidr.launcher.di

import javax.inject.Qualifier

/**
 * Qualifies the OpenAI-compatible cloud [com.sidr.launcher.domain.ai.GenerativeAiEngine] (Block K).
 *
 * The cloud engine is tagged rather than bound as the unqualified [GenerativeAiEngine] so Block M's
 * ordered `GenerativeRouter` can take the **unqualified** slot while composing the qualified cloud
 * engine + static fallback. Nothing injects this yet; the binding becomes live with the router.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudEngine
