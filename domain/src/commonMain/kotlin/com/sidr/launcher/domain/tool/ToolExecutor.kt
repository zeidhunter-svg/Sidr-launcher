package com.sidr.launcher.domain.tool

/**
 * Port: **the only path from the agent to the world.**
 *
 * `ToolExecutorCallSiteGuardTest` holds that mechanically, and holds it in two halves because neither
 * is sufficient alone. Across `domain/commonMain`, `data/repository`, `feature/launcher` and `app`,
 * over comment-stripped text, it asserts:
 *  1. exactly one call **spelled** `toolExecutor.invoke(`, and that it is in the file `AgentExecutor.kt`.
 *     A call through a differently named receiver would not match — which is why
 *  2. exactly three files **declare** the type (`:\s*ToolExecutor`): `AgentExecutor` (the holder),
 *     `AgentProvidesModule` (the binding) and `SystemIntentToolExecutor` (the one implementation). A
 *     new call site needs a new holder, so a fourth declaration is red before anything can call it.
 *
 * That is the mechanical version of the growth rule's promise: tool #21 gets consent for free not
 * because we will remember, but because there is nowhere to forget. It is a textual scan and its
 * blind spot is named rather than implied absent — reflection, or an implementation outside those four
 * roots, is out of its reach.
 *
 * **What that scan does NOT hold, and this KDoc must not be read as holding: WHERE inside
 * `AgentExecutor` the call sits.** It matches a file name, never a position. Today `checkpointFor` is
 * consulted in `prepare` and the call is in `perform`; move the call up into `prepare`, above the
 * checkpoint, and every assertion in that guard stays green while every CONFIRM-risk step fires before
 * consent is ever requested. What catches *that* is behavioural — `AgentExecutorTest`'s risk-transition
 * and denied-consent cases — so the two are complements, not substitutes. This sentence exists because
 * an earlier version of this KDoc claimed the position was mechanically held, the claim was copied into
 * the `DOC-HMA-2` matrix cell, and the A0 Task 14 review (2026-08-22) caught it in both places.
 *
 * [invoke] takes a [ResolvedInvocation] and never a [ToolInvocation] (F6). `InvocationValidator.resolve`
 * is the only producer of that type, so "an unbound reference cannot reach the world" is enforced by
 * the compiler rather than by a check a future edit could forget.
 */
interface ToolExecutor {
    suspend fun invoke(invocation: ResolvedInvocation): ToolResult
}
