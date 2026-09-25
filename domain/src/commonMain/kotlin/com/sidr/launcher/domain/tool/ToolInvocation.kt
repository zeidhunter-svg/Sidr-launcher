package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.intent.CommandFailure

/**
 * Where one argument's value comes from (F6).
 *
 * A plan is written entirely in terms of [ArgSource]; only the engine ever holds concrete values, and
 * only for the step it is about to run. That is also what makes a persisted plan re-bind against the
 * observations that actually survived a restart, rather than replay a value frozen at plan time.
 */
sealed interface ArgSource {

    /** The plan said this value outright. */
    data class Literal(val value: String) : ArgSource

    /**
     * The value produced by the step at [stepIndex] under [key].
     *
     * [stepIndex] must be **strictly earlier** than the step doing the binding, and [key] must appear
     * in that step's tool's [ToolDescriptor.outputSchema]. Both are checked by
     * `InvocationValidator.validate` before anything runs.
     */
    data class FromStep(val stepIndex: Int, val key: String) : ArgSource
}

/** What a plan says to call. Arguments may still be unresolved references — see [ResolvedInvocation]. */
data class ToolInvocation(val id: ToolId, val args: Map<String, ArgSource> = emptyMap())

/**
 * What is actually called. Every argument is a concrete value, so [ToolExecutor] **cannot** be handed
 * an unresolved reference — that is a property of the type system, not a convention someone has to
 * remember. `InvocationValidator.resolve` is the only function that constructs one.
 */
data class ResolvedInvocation(val id: ToolId, val args: Map<String, String> = emptyMap())

/**
 * What a tool produced for later steps. Keys must appear in the tool's [ToolDescriptor.outputSchema].
 *
 * Values are opaque strings in A0 — richer types wait for a real consumer, exactly as `ArgType` already
 * does for inputs.
 */
data class ToolOutput(val values: Map<String, String> = emptyMap())

/**
 * A fact a tool reported instead of performing an effect. This vocabulary is what makes a plan a
 * loop rather than a batch: without it "the app is not installed" is a dead end, and with it it is an
 * observation the next step's precondition can depend on.
 */
enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS }

/**
 * [Observed] turns "app not found" from a dead end into a fact the next step can depend on; [ToolOutput]
 * turns that fact into a **value** the next step can consume (F6). With only the first, every plan the
 * engine can express is a fallback chain; with both, a plan can compose.
 */
sealed interface ToolResult {

    /** The tool performed its side effect, and may have produced values for later steps. */
    data class Effected(val output: ToolOutput = ToolOutput()) : ToolResult

    /** The tool ran and reported [fact]; nothing on the device changed. */
    data class Observed(val fact: ObservedFact, val output: ToolOutput = ToolOutput()) : ToolResult

    /**
     * The tool handed the act to something outside the launcher and **cannot see what happened
     * next**. Not a success, not a failure, not an observation about the world: an outcome that has
     * not arrived.
     *
     * Two producers, and they are the same shape:
     *  - `uninstall_app` — `startActivity(ACTION_DELETE)` returns the moment the OS dialog is
     *    *raised*, identically whether the user then confirms, cancels, or the responder refuses
     *    silently (measured, rows 16/28/32). It is the only tool in the federation whose `Effected`
     *    could be false, and the only irreversible one, so this is where `DOC-ILM-3` (the trace is
     *    1:1 with reality) and `DOC-ILM-4` (a partial result is shown as partial) both bit.
     *  - a step cut by [RuntimeBudget.maxStepWallClockMs] — the call was made, we stopped waiting,
     *    and the side effect may well have happened.
     *
     * **This is not `ObservedFact` growing.** That type stays frozen at two values (A0.5's
     * record-don't-fix decision, held by `CoreVocabularyFreezeGuardTest`): `uninstall_app` has no
     * unsayable *fact*, it has an outcome that has not happened yet, which is a property of
     * execution rather than of the world. The seam is therefore here.
     *
     * [output] is carried for the same reason [Effected] carries one: a tool that declares an
     * `outputSchema` must honour it on **every** result, not on the branch it happened to take.
     */
    data class HandedOff(val output: ToolOutput = ToolOutput()) : ToolResult

    /**
     * Technical failure. [failure] is safe to display — no stack, no PII. Produces no output by
     * construction, which is why a step binding to a failed source is `UNRESOLVED_ARG_SOURCE` rather
     * than a blank.
     */
    data class Failed(val failure: CommandFailure) : ToolResult
}
