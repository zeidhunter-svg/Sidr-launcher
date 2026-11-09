package com.sidr.launcher.domain.text

import com.sidr.launcher.domain.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One name matcher (A4′ phase 3b, forks 3b-G and 3b-E; plan §0.2 B-2): a whole name is exact or a word
 * form, a form is only ever offered, and windows of the user's words go longest first. Fixtures are
 * invented (R14-43). «Плинта»/«Плинт» and «Velmo»/«Velmo Pad» share a stem ON PURPOSE — the edge rule and
 * the window order are what is under test — so assertions over them compare whole values by equality.
 */
class AppNameMatcherTest {

    private val folding = PlainTextFolding

    private val lumora = InstalledApp(packageName = "net.fixture.lumora", label = "Lumora")
    private val vemblic = InstalledApp(packageName = "net.fixture.vemblic", label = "Vemblic")
    private val miravin = InstalledApp(packageName = "net.fixture.miravin", label = "Миравин")
    private val sindaf = InstalledApp(packageName = "net.fixture.sindaf", label = "سنداف")
    private val plinta = InstalledApp(packageName = "net.fixture.plinta", label = "Плинта")
    private val plint = InstalledApp(packageName = "net.fixture.wexdor", label = "Плинт")
    private val velmo = InstalledApp(packageName = "net.fixture.velmo", label = "Velmo")
    private val velmoPad = InstalledApp(packageName = "net.fixture.quarnet", label = "Velmo Pad")
    private val ju = InstalledApp(packageName = "net.fixture.ju", label = "Ju")

    /** The kind of answer and its packages, in order — the pair's labels share a stem, so packages are compared (E4-P1). */
    private fun packagesOf(match: NameMatch): Pair<String, List<String>> = when (match) {
        is NameMatch.Exact -> "exact" to match.apps.map { it.packageName }
        is NameMatch.Edge -> "edge" to match.apps.map { it.packageName }
        NameMatch.None -> "none" to emptyList()
    }

    @Test
    fun `an exact label wins over any edge match`() {
        val match = AppNameMatcher.matchWhole("плинт", listOf(plinta, plint), folding)

        assertEquals("exact" to listOf("net.fixture.wexdor"), packagesOf(match))
    }

    @Test
    fun `a russian case ending matches by edge`() {
        assertEquals(
            NameMatch.Edge(listOf(miravin)),
            AppNameMatcher.matchWhole("миравину", listOf(lumora, vemblic, miravin, sindaf), folding),
        )
    }

    /** The apostrophe between letters is folded away (B-1), so «Vemblic'i» is one word with a suffix. */
    @Test
    fun `a turkish suffix after an apostrophe matches by edge`() {
        assertEquals(
            NameMatch.Edge(listOf(vemblic)),
            AppNameMatcher.matchWhole("Vemblic'i", listOf(lumora, vemblic, miravin, velmo), folding),
        )
    }

    /** Clause (ii): «بال» (bi- + al-) is a three-letter prefix before the label; vowel marks fold away, so the vowelled name is exact. */
    @Test
    fun `an arabic prefix and vowel marks match`() {
        val installed = listOf(lumora, miravin, sindaf)

        assertEquals(NameMatch.Edge(listOf(sindaf)), AppNameMatcher.matchWhole("بالسنداف", installed, folding))
        assertEquals(NameMatch.Exact(listOf(sindaf)), AppNameMatcher.matchWhole("سِنْدَاف", installed, folding))
    }

    /**
     * Two letters in common is no stem: «jux» would pass every other condition of clause (i) against «Ju»
     * (ending 1, ratio 6 ≥ 6), and «kju» would pass clause (ii) (offset 1, nothing after) — only the
     * three-letter minimum refuses them.
     */
    @Test
    fun `a stem shorter than three letters never matches by edge`() {
        assertEquals(NameMatch.None, AppNameMatcher.matchWhole("jux", listOf(ju), folding))
        assertEquals(NameMatch.None, AppNameMatcher.matchWhole("kju", listOf(ju), folding))
    }

    /** «lum» shares a three-letter prefix with «Lumora» and its ending is three — but three letters are not two thirds of six. */
    @Test
    fun `a fragment of a longer word is not a form`() {
        assertEquals(NameMatch.None, AppNameMatcher.matchWhole("lum", listOf(lumora), folding))
    }

    /** Review Focus 1: «плинту» is a form of both labels; binding either would be a guess. */
    @Test
    fun `edge matches on two apps are both offered, never one picked`() {
        val match = AppNameMatcher.matchWhole("плинту", listOf(plinta, plint), folding)

        assertEquals("edge" to listOf("net.fixture.plinta", "net.fixture.wexdor"), packagesOf(match))
    }

    /** Phase 1's F1-2: extra words may name a different app, so a label shorter than the text is no whole match. */
    @Test
    fun `a whole name with extra words is no whole match`() {
        assertEquals(NameMatch.None, AppNameMatcher.matchWhole("lumora studio", listOf(lumora), folding))
    }

    @Test
    fun `windows go longest first and mark a short window`() {
        val installed = listOf(velmo, velmoPad)

        assertEquals(
            WindowMatch(NameMatch.Exact(listOf(velmoPad)), whole = true),
            AppNameMatcher.matchWindows("velmo pad", installed, folding),
        )
        assertEquals(
            WindowMatch(NameMatch.Exact(listOf(velmo)), whole = false),
            AppNameMatcher.matchWindows("velmo пожалуйста", installed, folding),
        )
    }
}
