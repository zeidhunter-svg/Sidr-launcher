package com.sidr.launcher.domain.tool

/**
 * Port: **the only path from the agent to the world.**
 *
 * `ToolExecutorCallSiteGuardTest` holds that mechanically, and holds it in two halves because neither
 * is sufficient alone. Across `domain/commonMain`, `data/repository`, `feature/launcher` and `app`,
 * over comment-stripped text, it asserts:
 *  1. exactly one call **spelled** `toolExecutor.invoke(`, and that it is in `AgentExecutor`, below the
 *     consent checkpoint. A call through a differently named receiver would not match — which is why
 *  2. exactly three files **declare** the type (`:\s*ToolExecutor`): `AgentExecutor` (the holder),
 *     `AgentProvidesModule` (the binding) and `SystemIntentToolExecutor` (the one implementation). A
 *     new call site needs a new holder, so a fourth declaration is red before anything can call it.
 *
 * That is the mechanical version of the growth rule's promise: tool #21 gets consent for free not
 * because we will remember, but because there is nowhere to forget. It is a textual scan and its
 * blind spot is named rather than implied absent — reflection, or an implementation outside those four
 * roots, is out of its reach.
 *
 * [invoke] takes a [ResolvedInvocation] and never a [ToolInvocation] (F6). `InvocationValidator.resolve`
 * is the only producer of that type, so "an unbound reference cannot reach the world" is enforced by
 * the compiler rather than by a check a future edit could forget.
 */
interface ToolExecutor {
    suspend fun invoke(invocation: ResolvedInvocation): ToolResult
}
