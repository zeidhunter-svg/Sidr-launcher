package com.sidr.launcher.domain.ai

/**
 * Opaque identifier for a generative-AI provider (e.g. "cloud-default", "cloud-compatible").
 *
 * Deliberately a value class over a raw [String], NOT an enum of vendors: adding a new provider
 * must require no change to `:domain`. Only the data-layer adapter that implements
 * [GenerativeAiEngine] interprets this value (to pick a base URL / wire format / auth header).
 */
@JvmInline
value class AiProviderId(val value: String)

/**
 * Opaque model identifier. The domain never interprets it — only the provider adapter maps it to a
 * concrete model on the wire. `null` on an [AiRequest] means "use the engine/router default".
 */
@JvmInline
value class AiModelId(val value: String)
