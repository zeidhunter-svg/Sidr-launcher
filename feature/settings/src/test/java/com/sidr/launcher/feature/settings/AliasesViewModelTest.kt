package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.ObserveAliasesUseCase
import com.sidr.launcher.domain.memory.alias.PruneUnavailableAliasesUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AliasesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save then observe surfaces explicit alias memory`() = runTest(testDispatcher) {
        val store = FakeAliasStore()
        val vm = buildViewModel(store = store, apps = installedApps("com.telegram" to "Telegram"))
        advanceUntilIdle()

        vm.onSave("  Work   Chat ", "com.telegram")
        advanceUntilIdle()

        val alias = vm.uiState.value.aliases.single()
        assertEquals("work chat", alias.phrase)
        assertEquals("Telegram", alias.targetLabel)
        assertEquals("com.telegram", alias.targetPackageName)
        assertEquals(SidrMemoryStatus.Active, alias.status)
        assertEquals(AliasEvidence.UserDeclared, alias.evidence)
        assertTrue(alias.localOnly)
    }

    @Test
    fun `delete removes alias through use case`() = runTest(testDispatcher) {
        val store = FakeAliasStore()
        store.upsert(Alias("bank", AliasTarget.App("com.bank"), createdAtEpochMs = 1L))
        val vm = buildViewModel(store = store, apps = installedApps("com.bank" to "Bank"))
        advanceUntilIdle()

        vm.onDelete(vm.uiState.value.aliases.single().stableId)
        advanceUntilIdle()

        assertTrue(store.observeAll().first().isEmpty())
        assertTrue(vm.uiState.value.aliases.isEmpty())
    }

    @Test
    fun `load prunes unavailable aliases`() = runTest(testDispatcher) {
        val store = FakeAliasStore()
        store.upsert(Alias("keep", AliasTarget.App("com.keep"), createdAtEpochMs = 1L))
        store.upsert(Alias("drop", AliasTarget.App("com.gone"), createdAtEpochMs = 2L))

        val vm = buildViewModel(store = store, apps = installedApps("com.keep" to "Keep"))
        advanceUntilIdle()

        assertEquals(listOf("keep"), store.observeAll().first().map { it.phrase })
        assertEquals(listOf("keep"), vm.uiState.value.aliases.map { it.phrase })
    }

    @Test
    fun `picker apps are mapped and sorted for the add form`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            apps = installedApps(
                "com.zed" to "Zed",
                "com.alpha" to "Alpha",
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "Zed"), vm.uiState.value.pickerApps.map { it.label })
    }

    @Test
    fun `observe error surfaces retryable safe error without crashing`() = runTest(testDispatcher) {
        val vm = buildViewModel(store = ThrowingObserveAliasStore())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.aliases.isEmpty())
        assertEquals("couldn't load aliases - retry", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.canRetry)
    }

    private fun buildViewModel(
        store: AliasStore = FakeAliasStore(),
        apps: FakeInstalledAppsRepository = installedApps("com.telegram" to "Telegram"),
    ): AliasesViewModel = AliasesViewModel(
        observeAliases = ObserveAliasesUseCase(store, apps),
        saveAlias = SaveAliasUseCase(store),
        deleteAlias = DeleteAliasUseCase(store),
        pruneUnavailableAliases = PruneUnavailableAliasesUseCase(store, apps),
        installedApps = apps,
        ioDispatcher = testDispatcher,
    )

    private fun installedApps(vararg apps: Pair<String, String>): FakeInstalledAppsRepository =
        FakeInstalledAppsRepository().apply {
            appsToReturn = apps.map { (packageName, label) -> InstalledApp(packageName, label) }
        }

    private class ThrowingObserveAliasStore : AliasStore {
        override suspend fun find(phrase: String): OperationResult<Alias?> = OperationResult.Success(null)
        override suspend fun upsert(alias: Alias): OperationResult<Unit> = OperationResult.Success(Unit)
        override suspend fun delete(phrase: String): OperationResult<Unit> = OperationResult.Success(Unit)
        override fun observeAll(): Flow<List<Alias>> = flow {
            throw IllegalStateException("dao down")
        }
    }
}
