package com.sidr.launcher.feature.permission_education

import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.common.navigation.Routes
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

    // Bare route (no feature arg) → VM defaults to WALLPAPER, preserving the Phase-4 behaviour.
    private fun vm() = PermissionEducationViewModel(checker, prefs, SavedStateHandle())

    // Routed feature (Block T) — the nav arg selects which PermissionFeature to educate.
    private fun vmFor(feature: PermissionFeature) = PermissionEducationViewModel(
        checker,
        prefs,
        SavedStateHandle(mapOf(Routes.PermissionEducation.ARG_FEATURE to feature.name)),
    )

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

    @Test
    fun `refreshStatus never downgrades PERMANENTLY_DENIED to DENIED`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        // Drive the VM into PERMANENTLY_DENIED via the request callback (the only source for it).
        viewModel.onPermissionResult(granted = false, canRequestAgain = false)
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.uiState.value.status)

        // The checker (over checkSelfPermission) can only report DENIED here — a refresh must NOT
        // silently downgrade the permanently-denied status.
        checker.setStatus(PermissionFeature.WALLPAPER, PermissionStatus.DENIED)
        viewModel.refreshStatus()

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.uiState.value.status)
    }

    @Test
    fun `refreshStatus still upgrades PERMANENTLY_DENIED to GRANTED`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = false)
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.uiState.value.status)

        // If the user grants it in system Settings, a refresh must reflect the live grant.
        checker.setStatus(PermissionFeature.WALLPAPER, PermissionStatus.GRANTED)
        viewModel.refreshStatus()

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.status)
    }

    // ── Block T: feature routing + dangerous RECORD_AUDIO lifecycle ─────────

    @Test
    fun `feature is routed from the SavedStateHandle nav arg`() = runTest {
        checker.setStatus(PermissionFeature.VOICE_INPUT, PermissionStatus.DENIED)
        val viewModel = vmFor(PermissionFeature.VOICE_INPUT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PermissionFeature.VOICE_INPUT, state.feature)
        // VOICE_INPUT.requestable was flipped to true in Block T.
        assertTrue(state.requestable)
    }

    @Test
    fun `unknown feature arg falls back to WALLPAPER`() = runTest {
        val viewModel = PermissionEducationViewModel(
            checker,
            prefs,
            SavedStateHandle(mapOf(Routes.PermissionEducation.ARG_FEATURE to "NOT_A_FEATURE")),
        )
        advanceUntilIdle()
        assertEquals(PermissionFeature.WALLPAPER, viewModel.uiState.value.feature)
    }

    @Test
    fun `dangerous mic revocation GRANTED to DENIED is reflected on refresh`() = runTest {
        // The core dangerous-permission case the Block-H debt was deferred for: RECORD_AUDIO can be
        // revoked in Settings while we are backgrounded. refreshStatus() MUST surface that downgrade.
        checker.setStatus(PermissionFeature.VOICE_INPUT, PermissionStatus.GRANTED)
        val viewModel = vmFor(PermissionFeature.VOICE_INPUT)
        advanceUntilIdle()
        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.status)

        // User revokes the mic in system Settings; on return checkSelfPermission reads DENIED.
        checker.setStatus(PermissionFeature.VOICE_INPUT, PermissionStatus.DENIED)
        viewModel.refreshStatus()

        // Genuine revocation reflected — we do NOT keep believing the mic is available.
        assertEquals(PermissionStatus.DENIED, viewModel.uiState.value.status)
    }

    @Test
    fun `mic PERMANENTLY_DENIED is preserved against an ambiguous DENIED re-read`() = runTest {
        val viewModel = vmFor(PermissionFeature.VOICE_INPUT)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = false)
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.uiState.value.status)

        // checkSelfPermission can only report DENIED; it must NOT downgrade an established permanent
        // denial (that would bounce the user from the Settings deep-link to a dead re-request button).
        checker.setStatus(PermissionFeature.VOICE_INPUT, PermissionStatus.DENIED)
        viewModel.refreshStatus()

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.uiState.value.status)
    }

    @Test
    fun `mic PERMANENTLY_DENIED upgrades to GRANTED when enabled in Settings`() = runTest {
        val viewModel = vmFor(PermissionFeature.VOICE_INPUT)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = false)
        checker.setStatus(PermissionFeature.VOICE_INPUT, PermissionStatus.GRANTED)
        viewModel.refreshStatus()

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.status)
    }
}
