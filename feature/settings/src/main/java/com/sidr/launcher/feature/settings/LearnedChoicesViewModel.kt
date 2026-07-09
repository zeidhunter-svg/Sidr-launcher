package com.sidr.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceView
import com.sidr.launcher.domain.memory.resolution.ObserveLearnedChoicesUseCase
import com.sidr.launcher.domain.memory.resolution.PruneUnavailableLearnedChoicesUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LearnedChoicesUiState(
    val choices: List<LearnedChoiceView> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearnedChoicesViewModel @Inject constructor(
    private val observeLearnedChoices: ObserveLearnedChoicesUseCase,
    private val deleteLearnedChoice: DeleteLearnedChoiceUseCase,
    private val pruneUnavailableLearnedChoices: PruneUnavailableLearnedChoicesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val reloadSignal = MutableStateFlow(0)

    val uiState: StateFlow<LearnedChoicesUiState> = reloadSignal
        .flatMapLatest { observeChoices() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LearnedChoicesUiState(),
        )

    fun retry() {
        reloadSignal.value = reloadSignal.value + 1
    }

    fun onDelete(key: CapabilityKey) {
        viewModelScope.launch(ioDispatcher) {
            try {
                deleteLearnedChoice.delete(key, ResolutionContext.None)
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // Best-effort management action: delete failures must not crash the settings surface.
            }
        }
    }

    private fun observeChoices(): Flow<LearnedChoicesUiState> = flow {
        emit(LearnedChoicesUiState(isLoading = true))
        pruneUnavailableBestEffort()
        emitAll(
            observeLearnedChoices.observe().map { choices ->
                LearnedChoicesUiState(
                    choices = choices.mapNotNull(::safeChoiceOrNull),
                    isLoading = false,
                )
            },
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(
            LearnedChoicesUiState(
                isLoading = false,
                errorMessage = LOAD_ERROR,
                canRetry = true,
            ),
        )
    }

    private suspend fun pruneUnavailableBestEffort() {
        try {
            pruneUnavailableLearnedChoices.prune()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            // Display-time prune is best-effort; the guarded observe path still drives the screen.
        }
    }

    private fun safeChoiceOrNull(choice: LearnedChoiceView): LearnedChoiceView? =
        try {
            choice.takeUnless {
                it.capabilityKey.query.isBlank() ||
                    it.targetPackageName.isBlank() ||
                    it.targetLabel.isBlank()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            null
        }

    private companion object {
        const val LOAD_ERROR = "couldn't load learned choices · retry"
    }
}
