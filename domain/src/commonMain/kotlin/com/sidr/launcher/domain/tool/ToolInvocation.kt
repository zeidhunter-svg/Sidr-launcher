package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.intent.CommandFailure

/** One validated call: which tool, with which arguments. */
data class ToolInvocation(val id: ToolId, val args: Map<String, String> = emptyMap())

/**
 * A fact a tool reported instead of performing an effect. This vocabulary is what makes a plan a
 * loop rather than a batch: without it "the app is not installed" is a dead end, and with it it is an
 * observation the next step's precondition can depend on.
 */
enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS }

sealed interface ToolResult {
    /** The tool performed its side effect. */
    data object Effected : ToolResult

    /** The tool ran and reported [fact]; nothing on the device changed. */
    data class Observed(val fact: ObservedFact) : ToolResult

    /** Technical failure. [failure] is safe to display — no stack, no PII. */
    data class Failed(val failure: CommandFailure) : ToolResult
}
