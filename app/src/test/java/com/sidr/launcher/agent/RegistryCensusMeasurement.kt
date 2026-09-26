package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **A measurement, not a guard** (the `SelectionDeclineMeasurement` precedent). It prints the
 * authored registry census in one machine-readable line so `tools/gate.sh` can quote a number that
 * came from the federation rather than from prose — §9.2, which exists because five diverging
 * registry counts are alive in the documents today.
 *
 * **It counts AUTHORED tools only, and says so in its own output.** The dynamic tier is
 * `ShortcutToolSource`, whose catalog is empty without the `android.app.role.HOME` role and which
 * cannot be enumerated off a device at all — measured 2026-09-21: the role was held by
 * `com.sec.android.app.launcher`, so that phone's registry was 20 authored tools and not 243. A
 * census that silently mixed the two tiers would be the sixth diverging number rather than the one
 * that replaces five.
 *
 * **Its one assertion is what keeps it honest about itself.** A census assembled from a source list
 * this file maintains by hand is as green when it forgets an adapter as when it does not — the exact
 * hand-written-column weakness `ToolPermissionCatalog`'s own KDoc names. So the id set is compared
 * against that catalog, which is a **separate** production statement over the same authored tools
 * (20 rows, P10): drop `SystemIntentToolSource` here and the sets differ by `launch_app` and
 * `play_store_search`, and this goes red instead of printing 18.
 */
class RegistryCensusMeasurement {

    /**
     * Grants everything, same reasoning as `DoctrineGuardTest.grantsEverything`: this file quantifies
     * over the registry's *contents*, not over the filter, so a fixture that withheld a tool would let
     * the census pass having counted fewer tools than the federation actually declares.
     */
    private val grantsEverything = PermissionPresence { true }

    private fun productionAuthoredDescriptors(): List<ToolDescriptor> =
        Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything).all() +
            SystemIntentToolSource(DefaultActionCatalog()).all() +
            MemoryToolSource().all()

    @Test
    fun `print the authored registry census`() {
        val authored: List<ToolDescriptor> = productionAuthoredDescriptors()
        val byLevel = authored.groupingBy { it.level.value }.eachCount().toSortedMap()

        println(
            "REGISTRY :: authored=${authored.size} · " +
                byLevel.entries.joinToString(" ") { "${it.key}=${it.value}" } +
                " · dynamic=not-countable-off-device",
        )

        assertEquals(
            "the census and ToolPermissionCatalog are two production statements about the same " +
                "authored set; a difference means this file forgot an adapter (or the catalog did)",
            ToolPermissionCatalog().rows().keys,
            authored.map { it.id }.toSet(),
        )
    }
}
