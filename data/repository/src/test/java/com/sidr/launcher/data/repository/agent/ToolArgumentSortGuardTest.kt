package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.SortedArg
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
            .filter { CatalogArgumentSorts().sortOf(it.id, "app")?.sort != ArgSorts.APP }
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
        val orphans = CatalogArgumentSorts().all().flatMap { (id, args) ->
            val names = descriptors[id]?.argSchema?.map { it.name } ?: return@flatMap listOf("${id.value}: no such tool")
            args.mapNotNull { (arg, sorted) ->
                when {
                    arg !in names -> "${id.value}: arg `$arg` is not declared"
                    sorted.labelArg != null && sorted.labelArg !in names ->
                        "${id.value}: labelArg `${sorted.labelArg}` is not declared"
                    else -> null
                }
            }
        }

        assertEquals(emptyList<String>(), orphans)
    }

    /**
     * Phase 3b (fork 3b-C): the three pure sorts are declared on the arguments they read. Asserted as the
     * whole non-app part of the catalog, by equality — a row on the wrong argument, a row missing, or a
     * row nobody asked for all go red here. That each names a DECLARED argument is the test above; that
     * each sort has a resolver is `SortRegistryGuardTest` (`:app`). Since phase 3b's Task 6 step 2b
     * (`ToolMatchPlanner`) and the bridge (`DeviceMentionBinder`) resolve these rows before any card —
     * and read no `labelArg` on them, which is why the `labelArg = null` below is load-bearing: a value
     * row that declared one would need a decision there, and goes red here first.
     */
    @Test
    fun `duration, clock time and url arguments are declared`() {
        val nonApp = CatalogArgumentSorts().all()
            .mapValues { (_, args) -> args.filterValues { it.sort != ArgSorts.APP } }
            .filterValues { it.isNotEmpty() }

        assertEquals(
            mapOf(
                Tier0ToolIds.SET_TIMER to mapOf("duration" to SortedArg(ArgSorts.DURATION, labelArg = null)),
                Tier0ToolIds.SET_ALARM to mapOf("time" to SortedArg(ArgSorts.CLOCK_TIME, labelArg = null)),
                InAppToolIds.OPEN_URL to mapOf("url" to SortedArg(ArgSorts.URL, labelArg = null)),
            ),
            nonApp,
        )
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
