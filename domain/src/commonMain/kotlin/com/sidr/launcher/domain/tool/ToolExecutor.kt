package com.sidr.launcher.domain.tool

/**
 * Port: **the only path from the agent to the world.**
 *
 * `ToolExecutorCallSiteGuardTest` asserts there is exactly one call site of [invoke] in the whole
 * codebase, and that it sits behind the consent checkpoint. That is the mechanical version of the
 * growth rule's promise: tool #21 gets consent for free not because we will remember, but because
 * there is nowhere to forget.
 */
interface ToolExecutor {
    suspend fun invoke(invocation: ToolInvocation): ToolResult
}
