package com.sidr.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.ObserveAliasesUseCase
import com.sidr.launcher.domain.memory.alias.PruneUnavailableAliasesUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
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

data class AliasesUiState(
    val aliases: List<AliasMemoryUiModel> = emptyList(),
    val pickerApps: List<AliasPickerAppUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AliasesViewModel @Inject constructor(
    private val observeAliases: ObserveAliasesUseCase,
    private val saveAlias: SaveAliasUseCase,
    private val deleteAlias: DeleteAliasUseCase,
    private val pruneUnavailableAliases: PruneUnavailableAliasesUseCase,
    private val installedApps: InstalledAppsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val reloadSignal = MutableStateFlow(0)

    val uiState: StateFlow<AliasesUiState> = reloadSignal
        .flatMapLatest { observeState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AliasesUiState(),
        )

    fun retry() {
        reloadSignal.value = reloadSignal.value + 1
    }

    fun onSave(phrase: String, packageName: String) {
        if (packageName.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            try {
                saveAlias.save(phrase, AliasTarget.App(packageName))
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // Best-effort management action: save failures must not crash Settings.
            }
        }
    }

    fun onDelete(stableId: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                deleteAlias.delete(stableId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // Best-effort management action: delete failures must not crash Settings.
            }
        }
    }

    private fun observeState(): Flow<AliasesUiState> = flow {
        emit(AliasesUiState(isLoading = true))
        pruneUnavailableBestEffort()
        val pickerApps = (installedApps.getInstalledApps() as? OperationResult.Success)
            ?.value
            .orEmpty()
            .map { it.toAliasPickerUiModel() }
            .sortedBy { it.label.lowercase() }
        emitAll(
            observeAliases.observe().map { aliases ->
                AliasesUiState(
                    aliases = aliases.map { it.toMemoryUiModel() },
                    pickerApps = pickerApps,
                    isLoading = false,
                )
            },
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(
            AliasesUiState(
                isLoading = false,
                errorMessage = LOAD_ERROR,
                canRetry = true,
            ),
        )
    }

    private suspend fun pruneUnavailableBestEffort() {
        try {
            pruneUnavailableAliases.prune()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            // Display-time prune is best-effort; observe still drives the visible list.
        }
    }

    private companion object {
        const val LOAD_ERROR = "couldn't load aliases - retry"
    }
}
