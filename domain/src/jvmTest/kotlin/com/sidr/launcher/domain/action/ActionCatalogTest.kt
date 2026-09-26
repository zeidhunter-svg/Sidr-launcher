package com.sidr.launcher.domain.action

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.domain.permission.PermissionFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [ActionCatalog] + [ActionDescriptor] (AIL-1). Exercises the port via
 * [FakeActionCatalog] with sample descriptors — the production catalog is registered in AIL-2, so
 * these prove the *types compose* (risk, category, argSchema, permission gate) and the port
 * enumerates / looks up as specified.
 */
class ActionCatalogTest {

    private val launchApp = ActionDescriptor(
        id = ActionIds.LAUNCH_APP,
        title = "Open app",
        description = "Launch an installed app by name",
        category = ActionCategory.APP,
        risk = ActionRiskLevel.SAFE,
        argSchema = listOf(
            ActionArg(name = "query", required = true, description = "the app's display name"),
        ),
    )

    private val openUrl = ActionDescriptor(
        id = ActionIds.OPEN_URL,
        title = "Open link",
        description = "Open a web address in the browser",
        category = ActionCategory.WEB,
        risk = ActionRiskLevel.CONFIRM,
        argSchema = listOf(ActionArg(name = "url", description = "the URL to open")),
    )

    @Test
    fun `catalog enumerates its registered descriptors`() {
        val catalog = FakeActionCatalog(listOf(launchApp, openUrl))
        assertEquals(listOf(launchApp, openUrl), catalog.all())
    }

    @Test
    fun `descriptor lookup finds a registered id and misses an unregistered one`() {
        val catalog = FakeActionCatalog(listOf(launchApp, openUrl))
        assertEquals(openUrl, catalog.descriptor(ActionIds.OPEN_URL))
        assertNull(catalog.descriptor(ActionIds.SHOW_APPS))
    }

    @Test
    fun `descriptor defaults - empty argSchema and no permission gate`() {
        val showApps = ActionDescriptor(
            id = ActionIds.SHOW_APPS,
            title = "Show apps",
            description = "Show the full app grid",
            category = ActionCategory.SYSTEM,
            risk = ActionRiskLevel.SAFE,
        )
        assertTrue(showApps.argSchema.isEmpty())
        assertNull(showApps.permissionGate)
    }

    @Test
    fun `argSchema captures type, required flag and permission gate`() {
        val arg = launchApp.argSchema.single()
        assertEquals("query", arg.name)
        assertEquals(ArgType.STRING, arg.type)
        assertTrue(arg.required)

        // A permission-gated descriptor round-trips its gate (consumed by AIL-5).
        val gated = openUrl.copy(permissionGate = PermissionFeature.WALLPAPER)
        assertEquals(PermissionFeature.WALLPAPER, gated.permissionGate)
    }

    @Test
    fun `risk model exposes the three levels`() {
        assertEquals(ActionRiskLevel.SAFE, launchApp.risk)
        assertEquals(ActionRiskLevel.CONFIRM, openUrl.risk)
        // DANGEROUS is defined (Stage 3) so the AIL-5 confirmation `when` stays total.
        assertEquals(3, ActionRiskLevel.entries.size)
    }
}
