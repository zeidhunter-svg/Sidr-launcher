package com.sidr.launcher.domain.ai

/**
 * Assembles the **minimal** outbound generation request (the Block-I [AiRequest] contract) from the
 * only context allowed to leave the device (Fork P5-3, fail-closed).
 *
 * Today the assistant input is essentially the user's typed command, and this builder keeps it that
 * way: a single [AiRole.USER] message holding the command **verbatim**, a short static, context-free
 * [systemPrompt], and generation limits. **No device / usage / calendar / location / history /
 * contacts / clipboard context is assembled** — the positive allow-list lives in
 * [OutboundContextPolicy.ALLOWED], and adding a context category is a deliberate, reviewed change
 * (anything not allow-listed cannot reach an [AiRequest]).
 *
 * Pure: stdlib only, no Android / Ktor / serialization / reflection. The public surface is
 * intentionally tiny — [build] takes only the user command. There is **no overload that accepts a
 * context bag**: adding context must be a deliberate edit here, not an open door.
 */
class PromptContextBuilder(
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) {
    /**
     * Assembles the minimal outbound request: one [AiRole.USER] message whose content is
     * [userCommand] **unmodified**, plus the static [systemPrompt] and [maxOutputTokens]. [model] is
     * left `null` so the engine/router uses its configured default (the free-text model is provider
     * config — Block K, never pinned here). [userCommand] is the ONLY user-controlled content that
     * leaves the device.
     */
    fun build(userCommand: String): AiRequest =
        AiRequest(
            messages = listOf(AiMessage(AiRole.USER, userCommand)),
            system = systemPrompt,
            maxOutputTokens = maxOutputTokens,
        )

    companion object {
        /**
         * Short, static, context-free assistant instruction. It carries **no** denylist term and
         * **no** device/user context, and pins **no** model name (the model is free-text config —
         * Block K). [OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS] is guard-scanned over this text.
         */
        const val DEFAULT_SYSTEM_PROMPT: String =
            "You are the Sidr launcher assistant. Answer the user clearly and concisely."

        /** Phone-reasonable output budget — guidance, not a hard pin. */
        const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 512
    }
}
