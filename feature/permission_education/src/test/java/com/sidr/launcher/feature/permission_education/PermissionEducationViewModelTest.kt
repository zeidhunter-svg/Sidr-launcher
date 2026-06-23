package com.sidr.launcher.feature.permission_education

import com.sidr.launcher.core.testing.FakePermissionChecker
import com.sidr.launcher.core.testing.FakePermissionPrefsRepository
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionEducationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val checker = FakePermissionChecker(defaultStatus = PermissionStatus.DENIED)
    private val prefs = FakePermissionPrefsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = PermissionEducationViewModel(checker, prefs)

    @Test
    fun `initial state reflects the checker status and is requestable for wallpaper`() = runTest {
        checker.setStatus(PermissionFeature.WALLPAPER, PermissionStatus.GRANTED)
        val viewModel = vm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PermissionFeature.WALLPAPER, state.feature)
        assertEquals(PermissionStatus.GRANTED, state.status)
        assertTrue(state.requestable)
        assertFalse(state.dismissed)
    }

    @Test
    fun `granted result sets status GRANTED`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = true, canRequestAgain = false)

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.status)
    }

    @Test
    fun `denied but can ask again sets status DENIED — feature off, still requestable`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = true)

        val state = viewModel.uiState.value
        assertEquals(PermissionStatus.DENIED, state.status)
        // Denial disables exactly this feature; the feature is still offerable again.
        assertTrue(state.requestable)
    }

    @Test
    fun `denied and cannot ask again sets status PERMANENTLY_DENIED`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = false)

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.uiState.value.status)
    }

    @Test
    fun `onDismissForever persists per-feature and marks state dismissed`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onDismissForever()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dismissed)
        assertEquals(listOf(PermissionFeature.WALLPAPER to true), prefs.setCalls)
    }

    @Test
    fun `previously dismissed flag is reflected in state on init`() = runTest {
        prefs.setDismissed(PermissionFeature.WALLPAPER, true)
        val viewModel = vm()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dismissed)
    }

    @Test
    fun `onDismissForever swallows a persistence error and keeps state dismissed`() = runTest {
        prefs.errorToReturn = OperationError.UnknownError(reason = "boom")
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onDismissForever() // must not throw
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dismissed)
    }

    @Test
    fun `refreshStatus re-reads the checker`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()
        assertEquals(PermissionStatus.DENIED, viewModel.uiState.value.status)

        checker.setStatus(PermissionFeature.WALLPAPER, PermissionStatus.GRANTED)
        viewModel.refreshStatus()

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.status)
    }
}
