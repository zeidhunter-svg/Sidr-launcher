package com.sidr.launcher.domain.ai.local

/**
 * Opaque model identifier. Only the local-AI layer interprets this value — the domain
 * never parses it. Mirrors [com.sidr.launcher.domain.ai.AiModelId] in intent/purpose.
 */
@JvmInline
value class ModelId(val value: String)
