package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The bridge between the spelling convention and the declaration that replaces it**, and the only
 * thing that keeps the migration from being a silent downgrade.
 *
 * Before A4' phase 0 the string `"app"` WAS the contract. A row-based catalog is only safer than a
 * literal if the rows are total over the tools the literal used to catch — otherwise a tool that
 * used to be resolved silently stops being, which is the same class of failure as R14-37 with the
 * sign flipped.
 */
class ToolArgumentSortGuardTest {

    @Test
    fun `every production tool declaring an app argument has a sort row`() {
        val offenders = productionAuthoredDescriptors()
            .filter { d -> d.argSchema.any { it.name == "app" } }
            .filter { ToolArgumentSorts().appBindingFor(it.id) == null }
            .map { it.id.value }

        assertEquals(
            "a tool that declares an `app` argument and no sort row silently stops being resolved — " +
                "the R14-37 failure mode with the sign flipped",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no sort row names an argument its tool does not declare`() {
        val descriptors = productionAuthoredDescriptors().associateBy { it.id }
        val orphans = ToolArgumentSorts().rows().mapNotNull { (id, binding) ->
            val names = descriptors[id]?.argSchema?.map { it.name } ?: return@mapNotNull "${id.value}: no such tool"
            when {
                binding.arg !in names -> "${id.value}: arg `${binding.arg}` is not declared"
                binding.labelArg != null && binding.labelArg !in names ->
                    "${id.value}: labelArg `${binding.labelArg}` is not declared"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), orphans)
    }

    /**
     * The three AUTHORED sources, built the way `Tier0IntentToolSourceTest` builds its one. Grant-
     * everything is load-bearing, not a convenience: `Tier0IntentToolSource` filters by held
     * permission, and a presence that granted nothing would empty the list both tests quantify over
     * and make them pass by absence. The dynamic `app_shortcut` source is not here — its tools'
     * names are data and none declares an argument.
     */
    private fun productionAuthoredDescriptors(): List<ToolDescriptor> =
        Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }).all() +
            SystemIntentToolSource(DefaultActionCatalog()).all() +
            MemoryToolSource().all()
}
