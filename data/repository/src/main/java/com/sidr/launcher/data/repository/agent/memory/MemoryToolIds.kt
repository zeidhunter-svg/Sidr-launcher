package com.sidr.launcher.data.repository.agent.memory

import com.sidr.launcher.domain.tool.ToolId

/**
 * The three `launcher_memory` ids (Task 9, A1″ Phase 3a). Not projections of `ActionIds` and not Tier-0
 * intents — they mint their own ids, the same shape [com.sidr.launcher.data.repository.agent.Tier0ToolIds]
 * already uses for a source that is not `ActionCatalog`.
 */
object MemoryToolIds {
    val SET_APP_ALIAS = ToolId("set_app_alias")
    val FORGET_APP_ALIAS = ToolId("forget_app_alias")
    val FORGET_LEARNED_CHOICE = ToolId("forget_learned_choice")
}

/**
 * What the two alias tools report, so an undo can be told honestly (A4′ phase 2, spec §6.2, plan §0.3
 * P7): the key actually written — `SaveAliasUseCase` normalizes it, `forget_app_alias` does not — and
 * the package the key pointed at BEFORE, blank when there was none.
 */
object MemoryToolKeys {
    const val STORED_PHRASE = "stored_phrase"
    const val PREVIOUS_APP = "previous_app"
}
