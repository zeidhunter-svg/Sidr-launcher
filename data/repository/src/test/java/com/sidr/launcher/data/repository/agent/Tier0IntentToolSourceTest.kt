package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A1' Task 6: the federation's second real source. Two Android system intents that are not among the
 * frozen seven `ActionIds`, so they mint their own [com.sidr.launcher.domain.tool.ToolId]s rather than
 * projecting `ActionCatalog` the way [SystemIntentToolSource] does.
 *
 * A1" Phase 3a Task 2 adds the other half: what the source withholds. The two families of test below
 * are deliberately built on **opposite** fixtures, and which one a test gets is not a convenience —
 * see [grantsEverything].
 */
class Tier0IntentToolSourceTest {

    private class FakePresence(private val granted: Set<String>) : PermissionPresence {
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    /**
     * The fixture for every test whose subject is the registry's **contents**.
     *
     * It is load-bearing, not a convenience. A source that filters by permission can make a
     * contents test pass by **absence**: the test would quantify over a list the filter had already
     * emptied and assert nothing, exactly the vacuous-guard shape this repository has recorded
     * before (`ToolVocabularyLocaleGuardTest`'s KDoc, and `DoctrineGuardTest.productionAdapters`'
     * floor). Granting everything keeps those tests strict — they see the full shipped set and
     * assert against it. A fixture that grants **nothing** belongs only in the three tests below
     * whose subject *is* the filter.
     */
    private val grantsEverything = PermissionPresence { true }

    @Test
    fun `the tier-0 source offers three tools at the system-intent level, all external and safe`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(
            listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS, Tier0ToolIds.SET_ALARM),
            source.all().map { it.id },
        )
        assertEquals(true, source.all().all { it.level == ToolLevels.SYSTEM_INTENT })
        assertEquals(true, source.all().all { it.effect == ToolEffect.EXTERNAL })
        assertEquals(true, source.all().none { requiresConsent(it.risk) })
    }

    @Test
    fun `set_timer takes a required duration and open_system_settings takes nothing`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(listOf("duration"), source.find(Tier0ToolIds.SET_TIMER)!!.argSchema.map { it.name })
        assertEquals(emptyList<String>(), source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS)!!.argSchema.map { it.name })
    }

    @Test
    fun `set_alarm takes a required time`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(listOf("time"), source.find(Tier0ToolIds.SET_ALARM)!!.argSchema.map { it.name })
    }

    /**
     * **The floor Task 2's own filter made necessary** (fix round 1, finding I1).
     *
     * Before Task 2, a Tier-0 tool with no [ToolPermissionCatalog] row was still *registered*, so
     * `ToolRegistryPermissionGuardTest`'s `every registered tool has a permission row` could see it and
     * go red. After Task 2 it is withheld by `available()`, so it is registered nowhere — and a guard
     * that quantifies over the registry has nothing left to find. That inverts the 2026-09-05 defect
     * from "registered but refused" into "silently never registered", which is worse: nothing is red
     * anywhere, because a newly-added id is not in that guard's hand-written `REQUIRED_TOOL_IDS`
     * either.
     *
     * The expected list is spelled as **its own literal** and never derived from
     * [ToolPermissionCatalog] — deriving it would make the assertion agree with itself whatever the
     * catalog says, which is the identical reason `REQUIRED_TOOL_IDS` is hand-written rather than
     * read from production.
     *
     * **Adding a Tier-0 tool means extending two lists**: this one, and `REQUIRED_TOOL_IDS` in both
     * `ToolRegistryPermissionGuardTest` and `DoctrineGuardTest` (`:app`). Equality, not containment,
     * so a descriptor that silently stops being registered is red here too.
     *
     * **What it catches and what it does not, measured rather than assumed.** Removing a shipped
     * tool's catalog row turns this RED — verified by deleting `SET_TIMER`'s row and watching it fail
     * (`expected:<[set_timer, open_system_settings]> but was:<[open_system_settings]>`). Adding a
     * descriptor *and* extending this list while forgetting the row is also RED. What it cannot catch
     * is a descriptor added with **neither** a row nor a line here: `available()` drops it, `all()` is
     * unchanged, and the equality still holds. Closing that last case needs the source to expose its
     * *declared* descriptors, which is a production API this fix round did not open.
     */
    @Test
    fun `every tool this source declares is registered when every permission is held`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(
            "A Tier-0 descriptor with no ToolPermissionCatalog row is withheld at runtime and " +
                "registered nowhere, so no :app guard can see it. This list is where that absence " +
                "turns red. Adding a tool? Extend this list AND REQUIRED_TOOL_IDS in both :app guards.",
            listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS, Tier0ToolIds.SET_ALARM),
            source.all().map { it.id },
        )
    }

    @Test
    fun `a tool whose permission is not held is not advertised`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))

        assertEquals(emptyList<ToolId>(), source.all().map { it.id }.filter { it == Tier0ToolIds.SET_TIMER })
        assertNull(source.find(Tier0ToolIds.SET_TIMER))
    }

    @Test
    fun `a tool needing nothing is advertised even when no permission is held`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))

        assertNotNull(source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS))
    }

    @Test
    fun `find never answers for a tool all() withholds`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))
        val advertised = source.all().map { it.id }.toSet()

        listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS, Tier0ToolIds.SET_ALARM).forEach { id ->
            assertEquals("find/all disagree for ${id.value}", id in advertised, source.find(id) != null)
        }
    }
}
