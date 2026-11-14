package com.sidr.launcher.domain.sort

import com.sidr.launcher.domain.tool.ToolId

/**
 * Port: which arguments of which tools carry a sort (A4′ phase 3b, fork 3b-C). **A parallel port,
 * never a field on `ToolDescriptor`** — the phase 0 precedent, now generalised from "which argument
 * carries an app" to every sort. Each consumer implements its own: `:data:repository` for the
 * launcher's authored tools (`CatalogArgumentSorts`); `:consumer:jvm` for the sandbox
 * (`SandboxArgumentSorts`, phase 3b Task 13).
 *
 * **A missing row is not "resolve it anyway".** An argument with no sort is passed through as the
 * words that produced it; resolving an undeclared argument would be the guess every resolver exists to
 * refuse.
 */
interface ArgumentSorts {

    /** The sort declared for [arg] of [tool], or `null` when none is. */
    fun sortOf(tool: ToolId, arg: String): SortedArg?

    /** Every declaration: tool → argument name → its sort. */
    fun all(): Map<ToolId, Map<String, SortedArg>>
}
