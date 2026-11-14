package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.sort.SortCandidate
import com.sidr.launcher.domain.sort.SortResolution
import com.sidr.launcher.domain.text.PlainTextFolding
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The app sort's resolver is a thin adapter (A4′ phase 3b, fork 3b-C) — and a thin adapter is exactly
 * where one answer silently becomes another. Each [AppCandidates] value is produced by the REAL
 * [AppTargetResolver] over invented fixtures (R14-43) and compared with its [SortResolution] by equality,
 * so the five rows cannot drift apart without this test seeing which one moved.
 */
class AppSortResolverTest {

    private val lumora = InstalledApp(packageName = "net.fixture.lumora", label = "Lumora")
    private val tessarOne = InstalledApp(packageName = "net.fixture.tessar.one", label = "Tessar")
    private val tessarTwo = InstalledApp(packageName = "net.fixture.tessar.two", label = "Tessar")
    private val miravin = InstalledApp(packageName = "net.fixture.miravin", label = "Миравин")

    private fun resolverOver(apps: List<InstalledApp>, fails: Boolean = false) = AppSortResolver(
        AppTargetResolver(
            FakeInstalledAppsRepository().apply {
                appsToReturn = apps
                if (fails) errorToReturn = OperationError.UnknownError("io")
            },
            FakeAliasStore(),
            PlainTextFolding,
        ),
    )

    @Test
    fun `each candidate answer maps to its sort resolution`() = runTest {
        val installed = resolverOver(listOf(lumora, tessarOne, tessarTwo, miravin))

        // AppCandidates.None
        assertEquals(SortResolution.NotFound, installed.resolve("вельтрикс"))
        // AppCandidates.Unavailable
        assertEquals(SortResolution.Unavailable, resolverOver(emptyList(), fails = true).resolve("lumora"))
        // AppCandidates.One
        assertEquals(SortResolution.Resolved("net.fixture.lumora"), installed.resolve("lumora"))
        // AppCandidates.Several
        assertEquals(
            SortResolution.Choice(
                candidates = listOf(
                    SortCandidate(value = "net.fixture.tessar.one", label = "Tessar"),
                    SortCandidate(value = "net.fixture.tessar.two", label = "Tessar"),
                ),
                confirm = false,
            ),
            installed.resolve("tessar"),
        )
        // AppCandidates.Confirm
        assertEquals(
            SortResolution.Choice(candidates = listOf(SortCandidate(value = "net.fixture.miravin", label = "Миравин")), confirm = true),
            installed.resolve("миравину"),
        )
    }
}
