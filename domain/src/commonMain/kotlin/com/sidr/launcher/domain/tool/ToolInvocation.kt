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
     * Technical failure. [failure] is safe to display — no stack, no PII. Produces no output by
     * construction, which is why a step binding to a failed source is `UNRESOLVED_ARG_SOURCE` rather
     * than a blank.
     */
    data class Failed(val failure: CommandFailure) : ToolResult
}
