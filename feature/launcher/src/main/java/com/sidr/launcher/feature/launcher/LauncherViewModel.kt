package com.sidr.launcher.feature.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // ── Navigation events (Channel pattern from 3.1.x — unchanged) ────────
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    fun navigateTo(route: String) {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(route))
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

    // ── App-list state ─────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<UiState<LauncherUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<LauncherUiState>> = _uiState.asStateFlow()

    // ── Command input — independent of app-list loading ────────────────────
    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = UiState.Loading
            _uiState.value = when (val result = installedAppsRepository.getInstalledApps()) {
                is OperationResult.Success -> {
                    val apps = result.value
                    if (apps.isEmpty()) UiState.Empty
                    else UiState.Success(LauncherUiState(apps = apps))
                }
                is OperationResult.Failure -> UiState.Error(result.error.toUiError())
            }
        }
    }

    // ── UI actions ─────────────────────────────────────────────────────────

    fun onCommandChanged(text: String) {
        _commandInput.value = text
    }

    fun onCommandSubmitted(text: String) {
        // TODO: wired to HandleUserCommandUseCase in D3/D4
    }

    fun onAppClicked(app: InstalledApp) {
        // TODO: replaced by ActionExecutor in D2
    }

    // ── OperationError → UiError — exhaustive when, no else branch ─────────
    // Add a new branch here whenever OperationError gains a new subtype.
    private fun OperationError.toUiError(): UiError = when (this) {
        is OperationError.NetworkError     -> UiError.Network
        is OperationError.AiUnavailable    -> UiError.Unknown
        is OperationError.PermissionDenied -> UiError.Message("Permission denied: $permission")
        is OperationError.DeviceNotCapable -> UiError.Message("Not supported: $feature")
        is OperationError.UnknownError     -> UiError.Unknown
    }
}
