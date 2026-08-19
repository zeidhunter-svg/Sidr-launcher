package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasManagementUseCasesTest {
    private val store = FakeAliasStore()
    private val apps = FakeInstalledAppsRepository()

    @Test
    fun `observe hides aliases whose target is uninstalled and resolves label`() = runTest {
        apps.appsToReturn = listOf(InstalledApp("com.telegram", "Telegram"))
        store.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 1L))
        store.upsert(Alias("dead", AliasTarget.App("com.gone"), 2L))

        val views = ObserveAliasesUseCase(store, apps).observe().first()

        assertEquals(listOf(AliasView("work chat", "com.telegram", "Telegram")), views)
    }

    @Test
    fun `delete removes by phrase`() = runTest {
        store.upsert(Alias("bank", AliasTarget.App("com.a"), 1L))

        DeleteAliasUseCase(store).delete("bank")

        assertEquals(0, store.observeAll().first().size)
    }

    @Test
    fun `prune deletes aliases with uninstalled targets`() = runTest {
        apps.appsToReturn = listOf(InstalledApp("com.a", "A"))
        store.upsert(Alias("keep", AliasTarget.App("com.a"), 1L))
        store.upsert(Alias("drop", AliasTarget.App("com.gone"), 2L))

        PruneUnavailableAliasesUseCase(store, apps).prune()

        assertEquals(listOf("keep"), store.observeAll().first().map { it.phrase })
    }
}
