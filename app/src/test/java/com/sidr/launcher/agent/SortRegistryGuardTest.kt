package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.data.repository.agent.AppSortResolver
import com.sidr.launcher.data.repository.agent.AppTargetResolver
import com.sidr.launcher.data.repository.agent.CatalogArgumentSorts
import com.sidr.launcher.di.SortProvidesModule
import com.sidr.launcher.domain.sort.ArgSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **A sort the catalog declares and nobody can resolve is a tool argument that is silently never
 * understood** (A4′ phase 3b, fork 3b-C) — the same class of failure as a tool registered and dead.
 * The two halves live in different modules (`CatalogArgumentSorts` in `:data:repository`, the registry
 * in `:app`'s `SortProvidesModule`), so only a test that reads both can hold them together.
 *
 * "The production graph" is read the `BridgePayloadGuardTest` way: by calling the module's own
 * `@Provides` function — what Hilt reaches is what that function returns. The app resolver's
 * dependencies are fakes; only the registry's KEYS are read. Compared as sets of values, never by text.
 */
class SortRegistryGuardTest {

    @Test
    fun `every sort the catalog declares has a resolver in the production graph`() {
        val declared: Set<ArgSort> = CatalogArgumentSorts().all().values.flatMap { it.values }.map { it.sort }.toSet()
        val registered: Set<ArgSort> = SortProvidesModule.provideSortResolvers(
            AppSortResolver(AppTargetResolver(FakeInstalledAppsRepository(), FakeAliasStore())),
        ).keys

        // The control: "nothing missing" must not mean "nothing declared".
        assertNotEquals(emptySet<ArgSort>(), declared)
        assertEquals(
            "a sort the catalog declares has no resolver in SortProvidesModule — its arguments would " +
                "never be understood",
            emptySet<ArgSort>(),
            declared - registered,
        )
    }
}
