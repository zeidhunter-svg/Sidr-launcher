package com.sidr.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.SuggestionScheduling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Host ViewModel for the real launcher settings surface (Block X5). Lives in `:feature:settings`;
 * all deps are `:domain` ports (no `:data` / `:app` edge).
 *
 * MVP slice (Fork X5-B): **theme**, **AI suggestions**, **Assistant provider entry**,
 * **Set-as-default** (the last is a pure UI intent fired from the screen, not through this VM).
 *
 * The AI-suggestions toggle keeps its Block-W behaviour: write the flag, then re-sync WorkManager
 * via the [SuggestionScheduling] domain port (Fork X5-A) so the gate-before-enqueue contract holds
 * without a `feature → :app` edge. Navigation to the Assistant provider form is emitted as a
 * [NavigationEvent] and performed by the app-level NavHost — the key-invariant form is never
 * duplicated here.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val featureFlagRepository: FeatureFlagRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val suggestionScheduling: SuggestionScheduling,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    private val saveError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        featureFlagRepository.getFlags(),
        userPreferencesRepository.getPreferences(),
        saveError,
    ) { flags, preferences, errorMessage ->
        SettingsUiState(
            aiSuggestionsEnabled = flags.aiSuggestionsEnabled,
            usageHistoryEnabled = flags.usageHistoryEnabled,
            themeName = preferences.themeName,
            accentColor = preferences.accentColor,
            favoritesCount = preferences.favoritesCount,
            micInputEnabled = preferences.micInputEnabled,
            llmRouterEnabled = flags.llmRouterEnabled,
            errorMessage = errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
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
                suggestionScheduling.ensureScheduled()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    fun setThemeName(themeName: String) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = userPreferencesRepository.getPreferences().first()
                if (current.themeName != themeName) {
                    when (userPreferencesRepository.updatePreferences(current.copy(themeName = themeName))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> saveError.value = SAVE_ERROR
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    /**
     * Persist the brand accent (AIL-6 / DF-7). `green` is the default; `amber` is the alternative.
     * Applied immediately by `LauncherActivity` (which reads `UserPreferences.accentColor` reactively)
     * and survives restart. No-op when unchanged; a write failure surfaces a transient message.
     */
    fun setAccentColor(accentColor: String) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = userPreferencesRepository.getPreferences().first()
                if (current.accentColor != accentColor) {
                    when (userPreferencesRepository.updatePreferences(current.copy(accentColor = accentColor))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> saveError.value = SAVE_ERROR
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    /**
     * Persist the usage-history opt-in (Phase UX follow-up). When on, the launcher records app
     * launches so the home Favorites row and usage-based suggestion ranking can populate; off by
     * default (privacy-first). No-op when unchanged.
     */
    fun setUsageHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = featureFlagRepository.getFlags().first()
                if (current.usageHistoryEnabled != enabled) {
                    when (featureFlagRepository.updateFlags(current.copy(usageHistoryEnabled = enabled))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> saveError.value = SAVE_ERROR
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    /** Persist the home Favorites row size (Block X6). No-op when unchanged. */
    fun setFavoritesCount(count: Int) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = userPreferencesRepository.getPreferences().first()
                if (current.favoritesCount != count) {
                    when (userPreferencesRepository.updatePreferences(current.copy(favoritesCount = count))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> saveError.value = SAVE_ERROR
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    /**
     * Persist the AIL-4 LLM Action Router opt-in. Off by default (privacy-first + BYOK cost); when on,
     * low-confidence natural-language commands may be routed by the configured cloud LLM. No-op when
     * unchanged; a write failure surfaces a transient, display-safe message.
     */
    fun setLlmRouterEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = featureFlagRepository.getFlags().first()
                if (current.llmRouterEnabled != enabled) {
                    when (featureFlagRepository.updateFlags(current.copy(llmRouterEnabled = enabled))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> saveError.value = SAVE_ERROR
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    /** Persist the voice/mic input toggle (Block X6). No-op when unchanged. */
    fun setMicInputEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            saveError.value = null
            try {
                val current = userPreferencesRepository.getPreferences().first()
                if (current.micInputEnabled != enabled) {
                    when (userPreferencesRepository.updatePreferences(current.copy(micInputEnabled = enabled))) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> saveError.value = SAVE_ERROR
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                saveError.value = SAVE_ERROR
            }
        }
    }

    /** Route to the existing Assistant provider form (Block N); never duplicate the key-bearing form. */
    fun openAssistantProvider() {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(Routes.Assistant.ROUTE))
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

    private companion object {
        const val SAVE_ERROR = "Couldn't update launcher settings. Please try again."
    }
}
