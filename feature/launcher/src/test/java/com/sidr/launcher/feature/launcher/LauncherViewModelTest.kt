package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class LauncherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = FakeInstalledAppsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fakeRepo.reset()
    }

    private fun buildViewModel() = LauncherViewModel(fakeRepo, testDispatcher)

    // ── App list loading ───────────────────────────────────────────────────

    @Test
    fun `Loading before advanceUntilIdle then Success after for non-empty list`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.example.one", "One"),
                InstalledApp("com.example.two", "Two"),
            )
            val vm = buildViewModel()

            // init{} has queued loadApps() but it has not run yet
            assertTrue(
                "Expected Loading before advance, got ${vm.uiState.value}",
                vm.uiState.value is UiState.Loading,
            )

            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("Expected Success after advance, got $state", state is UiState.Success)
            assertEquals(fakeRepo.appsToReturn, (state as UiState.Success).data.apps)
        }

    @Test
    fun `empty apps list emits Empty state`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = emptyList()
        val vm = buildViewModel()

        advanceUntilIdle()

        assertTrue(
            "Expected Empty, got ${vm.uiState.value}",
            vm.uiState.value is UiState.Empty,
        )
    }

    @Test
    fun `repository is called exactly once on init`() = runTest(testDispatcher) {
        buildViewModel()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.callCount)
    }

    // ── OperationError → UiError mapping ──────────────────────────────────

    @Test
    fun `PermissionDenied maps to UiError_Message`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.PermissionDenied("QUERY_ALL_PACKAGES")
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue(
            "Expected UiError.Message, got ${(state as UiState.Error).error}",
            state.error is UiError.Message,
        )
    }

    @Test
    fun `NetworkError maps to UiError_Network`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertEquals(UiError.Network, (state as UiState.Error).error)
    }

    @Test
    fun `UnknownError maps to UiError_Unknown`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.UnknownError()
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertEquals(UiError.Unknown, (state as UiState.Error).error)
    }

    @Test
    fun `AiUnavailable maps to UiError_Unknown`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.AiUnavailable
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertEquals(UiError.Unknown, (state as UiState.Error).error)
    }

    @Test
    fun `DeviceNotCapable maps to UiError_Message`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.DeviceNotCapable("nlu")
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue(
            "Expected UiError.Message, got ${(state as UiState.Error).error}",
            state.error is UiError.Message,
        )
    }

    // ── commandInput independence ──────────────────────────────────────────

    @Test
    fun `commandInput updates independently and uiState stays Empty`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = emptyList()
            val vm = buildViewModel()

            vm.onCommandChanged("x")   // does not touch uiState

            advanceUntilIdle()

            assertTrue(
                "Expected Empty, got ${vm.uiState.value}",
                vm.uiState.value is UiState.Empty,
            )
            assertEquals("x", vm.commandInput.value)
        }

    @Test
    fun `commandInput starts empty before any coroutines run`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        // commandInput is a plain StateFlow — no coroutine needed to initialise it
        assertEquals("", vm.commandInput.value)
    }
}
