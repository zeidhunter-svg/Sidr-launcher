package com.sidr.launcher.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.work.SuggestionsWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LauncherSettingsViewModel @Inject constructor(
    private val featureFlagRepository: FeatureFlagRepository,
    private val suggestionsWorkScheduler: SuggestionsWorkScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val saveError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LauncherSettingsUiState> = combine(
        featureFlagRepository.getFlags(),
        saveError,
    ) { flags, errorMessage ->
        LauncherSettingsUiState(
            aiSuggestionsEnabled = flags.aiSuggestionsEnabled,
            errorMessage = errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherSettingsUiState(),
    )

    fun setAiSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = featureFlagRepository.getFlags().first()
                if (current.aiSuggestionsEnabled != enabled) {
                    when (featureFlagRepository.updateFlags(current.copy(aiSuggestionsEnabled = enabled))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> {
                            saveError.value = SAVE_ERROR
                            return@launch
                        }
                    }
                }
                suggestionsWorkScheduler.ensureScheduled()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    private companion object {
        const val SAVE_ERROR = "Couldn't update launcher settings. Please try again."
    }
}

data class LauncherSettingsUiState(
    val aiSuggestionsEnabled: Boolean = false,
    val errorMessage: String? = null,
)
