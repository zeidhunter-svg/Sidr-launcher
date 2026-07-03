package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerGroupingTest {

    private fun app(label: String) = InstalledApp("pkg.${label.lowercase()}", label)

    @Test
    fun `empty input yields no sections`() {
        assertTrue(groupIntoSections(emptyList()).isEmpty())
    }

    @Test
    fun `apps are sorted alphabetically within a section`() {
        val sections = groupIntoSections(listOf(app("Camera"), app("Calendar"), app("Calculator")))
        assertEquals(listOf("C"), sections.map { it.letter })
        assertEquals(
            listOf("Calculator", "Calendar", "Camera"),
            sections.single().apps.map { it.label },
        )
    }

    @Test
    fun `sections come out in A to Z order regardless of input order`() {
        val sections = groupIntoSections(listOf(app("Zoom"), app("Maps"), app("Books")))
        assertEquals(listOf("B", "M", "Z"), sections.map { it.letter })
    }

    @Test
    fun `grouping is case-insensitive on the first letter`() {
        val sections = groupIntoSections(listOf(app("android Auto"), app("Amazon")))
        assertEquals(listOf("A"), sections.map { it.letter })
        // Case-insensitive ordering: "Amazon" before "android Auto".
        assertEquals(listOf("Amazon", "android Auto"), sections.single().apps.map { it.label })
    }

    @Test
    fun `non-letter labels collapse into a trailing hash bucket`() {
        val sections = groupIntoSections(listOf(app("1Password"), app("Notes"), app("+Message")))
        assertEquals(listOf("N", "#"), sections.map { it.letter })
        // Both non-alpha apps land in "#", themselves alphabetically ordered.
        assertEquals(listOf("+Message", "1Password"), sections.last().apps.map { it.label })
    }

    @Test
    fun `hash bucket is omitted when every label starts with a letter`() {
        val sections = groupIntoSections(listOf(app("Alpha"), app("Beta")))
        assertEquals(listOf("A", "B"), sections.map { it.letter })
    }

    // ── filterApps (Block X4) ──────────────────────────────────────────────

    @Test
    fun `blank query returns the full list unchanged`() {
        val apps = listOf(app("Alpha"), app("Beta"))
        assertEquals(apps, filterApps(apps, ""))
        assertEquals(apps, filterApps(apps, "   "))
    }

    @Test
    fun `filter matches a substring anywhere in the label, case-insensitively`() {
        val apps = listOf(app("Telegram"), app("Calendar"), app("Settings"))
        // Prefix.
        assertEquals(listOf("Telegram"), filterApps(apps, "tele").map { it.label })
        // Mid-word substring + case-insensitive.
        assertEquals(listOf("Settings"), filterApps(apps, "TING").map { it.label })
    }

    @Test
    fun `filter can match multiple apps`() {
        val apps = listOf(app("Calendar"), app("Calculator"), app("Maps"))
        assertEquals(listOf("Calendar", "Calculator"), filterApps(apps, "cal").map { it.label })
    }

    @Test
    fun `query with no match yields an empty list`() {
        val apps = listOf(app("Alpha"), app("Beta"))
        assertTrue(filterApps(apps, "zzz").isEmpty())
    }

    @Test
    fun `query is trimmed before matching`() {
        val apps = listOf(app("Telegram"))
        assertEquals(listOf("Telegram"), filterApps(apps, "  tele  ").map { it.label })
    }
}
