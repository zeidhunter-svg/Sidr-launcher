package com.sidr.launcher.data.repository.action

import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultActionCatalogTest {

    private val catalog = DefaultActionCatalog()

    @Test fun `registers a descriptor for every action family`() {
        val ids = catalog.all().map { it.id }
        assertEquals(ActionIds.ALL.toSet(), ids.toSet())
        assertEquals("no duplicate descriptors", ActionIds.ALL.size, ids.size)
    }

    @Test fun `descriptor lookup returns the matching family`() {
        val d = catalog.descriptor(ActionIds.OPEN_URL)
        assertNotNull(d)
        assertEquals(ActionIds.OPEN_URL, d!!.id)
        assertEquals(ActionCategory.WEB, d.category)
    }

    @Test fun `lookup of an unregistered id is null`() {
        assertNull(catalog.descriptor(com.sidr.launcher.domain.action.ActionId("not_a_family")))
    }

    @Test fun `open url and play store are CONFIRM risk`() {
        assertEquals(ActionRiskLevel.CONFIRM, catalog.descriptor(ActionIds.OPEN_URL)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, catalog.descriptor(ActionIds.PLAY_STORE_SEARCH)!!.risk)
    }

    @Test fun `shipped families are SAFE risk`() {
        listOf(
            ActionIds.LAUNCH_APP,
            ActionIds.WEB_SEARCH,
            ActionIds.OPEN_SETTINGS,
            ActionIds.OPEN_ASSISTANT,
            ActionIds.SHOW_APPS,
        ).forEach { id ->
            assertEquals("expected SAFE for $id", ActionRiskLevel.SAFE, catalog.descriptor(id)!!.risk)
        }
    }

    @Test fun `arg-bearing families declare a required string arg`() {
        listOf(ActionIds.LAUNCH_APP, ActionIds.WEB_SEARCH, ActionIds.OPEN_URL, ActionIds.PLAY_STORE_SEARCH)
            .forEach { id ->
                val schema = catalog.descriptor(id)!!.argSchema
                assertEquals("expected exactly one arg for $id", 1, schema.size)
                assertTrue("arg should be required for $id", schema.single().required)
            }
    }

    @Test fun `no family requires a permission gate in the MVP`() {
        assertTrue(catalog.all().all { it.permissionGate == null })
    }
}
