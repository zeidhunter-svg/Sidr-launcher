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
    fun `the tier-0 source offers two tools at the system-intent level, both external and safe`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(
            listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS),
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

        listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS).forEach { id ->
            assertEquals("find/all disagree for ${id.value}", id in advertised, source.find(id) != null)
        }
    }
}
