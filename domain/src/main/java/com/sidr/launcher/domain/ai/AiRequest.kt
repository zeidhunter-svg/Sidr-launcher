package com.sidr.launcher.domain.ai

/** Conversation role for an [AiMessage]. Tool / system-in-array roles are deferred. */
enum class AiRole { USER, ASSISTANT }

/** A single content-only turn in the conversation. */
data class AiMessage(val role: AiRole, val content: String)

/**
 * Provider-neutral generation request.
 *
 * Intentionally carries **no sampling parameters** (`temperature`/`top_p`/`top_k`): some models
 * reject them with HTTP 400, and the allowed shape is model-specific — so sampling stays a per-
 * adapter concern, decided inside the data-layer adapter, never a domain field. [system] is a
 * top-level field; each adapter places it where its API expects (a top-level field, a leading
 * message, etc.). [model] of `null` lets the engine/router use its configured default.
 */
data class AiRequest(
    val messages: List<AiMessage>,
    val system: String? = null,
    val maxOutputTokens: Int,
    val model: AiModelId? = null,
    val stopSequences: List<String> = emptyList(),
)
