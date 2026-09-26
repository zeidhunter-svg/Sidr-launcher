package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPermissionCatalogTest {

    private val catalog = ToolPermissionCatalog()

    @Test
    fun `an unrowed id answers null, never an empty list`() {
        assertNull(catalog.permissionsFor(ToolId("nobody_declared_this")))
    }

    @Test
    fun `a tool that needs nothing has an explicit empty row`() {
        assertEquals(emptyList<String>(), catalog.permissionsFor(Tier0ToolIds.OPEN_SYSTEM_SETTINGS))
    }

    @Test
    fun `set_timer declares the permission row 27 measured as required`() {
        assertEquals(
            listOf("com.android.alarm.permission.SET_ALARM"),
            catalog.permissionsFor(Tier0ToolIds.SET_TIMER),
        )
    }

    @Test
    fun `rows is stable and non-empty, so a caller reading it twice sees one answer`() {
        // Controller ruling R14-23: this asserts stability and non-vacuity, NOT immutability.
        // `rows()` returns the companion's map directly and Kotlin's `Map` is already read-only, so
        // "a caller cannot mutate the catalog" would be a name promising a property no code holds.
        val first = catalog.rows()
        assertTrue(first.isNotEmpty())
        assertEquals(first, catalog.rows())
    }
}
