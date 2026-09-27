package com.sidr.launcher.domain.tool

/**
 * How one step that acted can be put back (A4′ phase 2, spec §6.2, owner fork F2-3.3).
 */
sealed interface Undo {
    /**
     * Put back by running [invocation]. Its arguments are concrete [ArgSource.Literal]s built from
     * what the step was called with and what it REPORTED — an undo that re-read the world would undo
     * something other than what happened.
     */
    data class By(val invocation: ToolInvocation) : Undo

    /** The step changed something that stays, and nothing registered puts it back. */
    data object NotUndoable : Undo

    /** The step left nothing that stays — a screen opened, a search shown. */
    data object NothingToUndo : Undo
}

/**
 * Port: which registered tool undoes which. **A parallel port, never a field on [ToolDescriptor]** —
 * the phase 0 precedent (`ToolArgumentSorts`); seventeen production files read the descriptor. Each
 * consumer implements its own: `:data:repository` for the launcher's tools, `:consumer:jvm` for the
 * sandbox. A tool an implementation does not know is [Undo.NotUndoable] — a missing answer is never
 * "nothing to undo".
 *
 * **Read by compensation only, never by the engine** (spec §6.3): an inverse the gate could see is an
 * inverse that could buy a step silence. `CompensationOutsideTheEngineGuardTest` (`:app`) holds that
 * `AgentExecutor.kt` names none of this.
 */
fun interface ToolInverses {
    /** How to undo [forward], which returned [result]. Only an `Effected` step reaches here. */
    fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo
}
