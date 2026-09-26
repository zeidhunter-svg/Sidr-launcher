package com.sidr.launcher.data.repository.agent

/**
 * Shared across [ToolSelectorTest] and `ToolVocabularyReachabilityTest` (Task 11) — both need the same
 * trivial [DynamicToolNames] fake over a fixed list, and duplicating it per file would let the two
 * drift the way [DynamicToolNames]'s own KDoc warns a split derivation can. A top-level `internal`
 * function in this test source set is the whole of what either caller needs.
 */
internal fun namesOf(vararg names: DynamicToolName): DynamicToolNames = object : DynamicToolNames {
    override fun names(): List<DynamicToolName> = names.toList()
}
