package com.sidr.launcher.feature.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.CityIndex
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationProvider
import com.sidr.launcher.domain.prayer.PrayerPreferencesRepository
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.prayer.SupportedPrayerMethod
import com.sidr.launcher.domain.prayer.SupportedPrayerMethods
import com.sidr.launcher.domain.result.OperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state for [PrayerSettingsScreen]. NO method/madhab is preselected — `MethodRequired`/an
 * unconfigured setup is a real, honest state (spec §0.1/§0.2), never guessed.
 */
data class PrayerSettingsUiState(
    val methodOptions: List<SupportedPrayerMethod> = SupportedPrayerMethods.ALL,
    val madhabOptions: List<Madhab> = Madhab.values().toList(),
    val selectedMethod: CalculationMethodId? = null,
    val selectedMadhab: Madhab? = null,
    val location: PrayerLocation? = null,
    val isConfigured: Boolean = false,
    val citySearchQuery: String = "",
    val citySearchResults: List<PrayerLocation> = emptyList(),
    val isResolvingDeviceLocation: Boolean = false,
    val statusMessage: String? = null,
)

/**
 * Prayer setup ViewModel (DS-6B Task 8). Android-free: injects only [PrayerPreferencesRepository],
 * [CityIndex], [GetPrayerContextUseCase], and [PrayerLocationProvider] — all domain ports. Holds NO
 * [com.sidr.launcher.domain.permission.PermissionChecker] and no Android permission API — the screen
 * owns the permission gate (checks/requests before calling [useDeviceLocation]); this VM only ever
 * calls the already-permission-checked, already-rounded [PrayerLocationProvider] port.
 *
 * Method + madhab are BOTH mandatory before anything can be persisted — [PrayerSetup] itself makes
 * both fields non-nullable (spec §0.1/§0.2), so [trySave] is a structural no-op until both are chosen;
 * location stays independently optional (a method+madhab-only setup is the real `LOCATION_MISSING`
 * state, not an error). Every selection commits immediately once both are known (no separate "Save"
 * button in the UI) — [saveSetup] is also exposed directly for an explicit save entry point.
 *
 * [preferences] is read via one-shot [Flow.firstOrNull] calls (the [GetPrayerContextUseCase]/
 * `SettingsViewModel.setThemeName`-style precedent), never a live, continuously-collected local
 * mirror: this VM is the screen's single writer for the whole time it is alive, so a fresh read
 * before each write is both sufficient and — critically — race-free. An earlier draft kept a
 * continuously-collected local copy of [PrayerSettingsUiState.selectedMethod]/
 * [PrayerSettingsUiState.selectedMadhab]/[PrayerSettingsUiState.location] driven by
 * [PrayerPreferencesRepository.setup]; that re-emission could resolve to a stale, pre-write value
 * (its own collector's asynchronous echo of A DIFFERENT in-flight write racing this one) and
 * clobber a just-made, not-yet-collector-visible selection — a real bug that a coroutine-ordering
 * test caught directly (a `useDeviceLocation()` call immediately after selecting method+madhab lost
 * the method/madhab out from under it before its own save could run). One-shot reads make ordering
 * irrelevant.
 */
@HiltViewModel
class PrayerSettingsViewModel @Inject constructor(
    private val preferences: PrayerPreferencesRepository,
    private val cityIndex: CityIndex,
    private val getPrayerContext: GetPrayerContextUseCase,
    private val locationProvider: PrayerLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerSettingsUiState())
    val uiState: StateFlow<PrayerSettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    init {
        // One-time initial seed only — see the class kdoc for why this is deliberately NOT a live
        // collector. Still guarded (not an unconditional overwrite): this read is itself async, and
        // a fast enough user action (select method, select madhab, ...) can complete — synchronously,
        // via `_uiState.update` — before this coroutine ever gets to run. Only apply the seed while
        // local selection state is still completely untouched (all three fields default); once the
        // user has touched anything, a late-arriving seed must never clobber it.
        viewModelScope.launch {
            val setup = preferences.setup().firstOrNull()
            _uiState.update { current ->
                val untouched = current.selectedMethod == null &&
                    current.selectedMadhab == null &&
                    current.location == null
                if (!untouched) {
                    current
                } else {
                    current.copy(
                        selectedMethod = setup?.methodId,
                        selectedMadhab = setup?.madhab,
                        location = setup?.location,
                        isConfigured = setup != null,
                    )
                }
            }
        }
    }

    fun onMethodSelected(id: CalculationMethodId) {
        _uiState.update { it.copy(selectedMethod = id) }
        trySave()
    }

    fun onMadhabSelected(madhab: Madhab) {
        _uiState.update { it.copy(selectedMadhab = madhab) }
        trySave()
    }

    fun onCitySearchQueryChanged(query: String) {
        _uiState.update { it.copy(citySearchQuery = query) }
        viewModelScope.launch {
            val results = cityIndex.search(query, CITY_SEARCH_LIMIT)
            // Guard against a stale, slower response clobbering a newer, faster one.
            if (_uiState.value.citySearchQuery == query) {
                _uiState.update { it.copy(citySearchResults = results) }
            }
        }
    }

    fun onCitySelected(location: PrayerLocation) {
        _uiState.update {
            it.copy(location = location, citySearchQuery = "", citySearchResults = emptyList())
        }
        trySave()
    }

    /**
     * Explicit save entry point (spec/brief). Requires [PrayerSettingsUiState.selectedMethod] AND
     * [PrayerSettingsUiState.selectedMadhab] — location is optional. A lone method-or-madhab choice
     * is not a valid [PrayerSetup] and is silently held as pending UI state until both are chosen
     * (never partially persisted).
     */
    fun saveSetup() = trySave()

    private fun trySave() {
        val state = _uiState.value
        val methodId = state.selectedMethod
        val madhab = state.selectedMadhab
        if (methodId == null || madhab == null) return

        viewModelScope.launch {
            when (preferences.saveSetup(PrayerSetup(methodId, madhab, state.location))) {
                is OperationResult.Success -> {
                    _uiState.update { it.copy(isConfigured = true, statusMessage = null) }
                    warmScheduleCache()
                }
                is OperationResult.Failure -> _uiState.update { it.copy(statusMessage = SAVE_ERROR) }
            }
        }
    }

    /** Clears the whole setup — returns to `NOT_CONFIGURED`. */
    fun clearSetup() {
        viewModelScope.launch {
            preferences.clearSetup()
            _uiState.value = PrayerSettingsUiState()
        }
    }

    /** Clears only the location; method/madhab (if set) are preserved — `LOCATION_MISSING`, not cleared. */
    fun clearLocation() {
        viewModelScope.launch {
            val current = preferences.setup().firstOrNull() ?: return@launch
            when (preferences.saveSetup(current.copy(location = null))) {
                is OperationResult.Success -> _uiState.update { it.copy(location = null, statusMessage = null) }
                is OperationResult.Failure -> _uiState.update { it.copy(statusMessage = SAVE_ERROR) }
            }
        }
    }

    /**
     * Reads a one-shot device location fix through [PrayerLocationProvider] — the port has ALREADY
     * checked permission and rounded the coordinates (spec §0.5); this function does neither. A
     * `Success(null)` (no fix) or [OperationResult.Failure] (read error) leaves the current setup
     * completely untouched — the city path (and any already-saved location) stays usable either way
     * (spec: "denial leaves everything usable").
     */
    fun useDeviceLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingDeviceLocation = true, statusMessage = null) }
            try {
                when (val result = locationProvider.currentLocation()) {
                    is OperationResult.Success -> {
                        val location = result.value
                        if (location != null) {
                            _uiState.update { it.copy(location = location, isResolvingDeviceLocation = false) }
                            trySave()
                        } else {
                            _uiState.update {
                                it.copy(isResolvingDeviceLocation = false, statusMessage = NO_DEVICE_FIX)
                            }
                        }
                    }
                    is OperationResult.Failure -> {
                        _uiState.update {
                            it.copy(isResolvingDeviceLocation = false, statusMessage = DEVICE_LOCATION_ERROR)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(isResolvingDeviceLocation = false, statusMessage = DEVICE_LOCATION_ERROR)
                }
            }
        }
    }

    /**
     * Best-effort cache warm-up after a successful save, so the (Task 9) Home strip and the detail
     * screen see a fresh schedule immediately rather than waiting for their own first read. Never
     * surfaces a failure here — [GetPrayerContextUseCase.get] already turns every failure into a
     * value ([com.sidr.launcher.domain.prayer.PrayerContext.Unavailable]), so this cannot throw an
     * expected error; [CancellationException] is re-thrown, everything else is swallowed.
     */
    private fun warmScheduleCache() {
        viewModelScope.launch {
            try {
                getPrayerContext.get().collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Best-effort warm-up only — never surfaced to the user.
            }
        }
    }

    fun openPermissionEducation() {
        _navigationEvents.trySend(
            NavigationEvent.NavigateTo(Routes.PermissionEducation.routeFor(PRAYER_LOCATION_FEATURE)),
        )
    }

    fun openPrayerDetail() {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(Routes.PrayerDetail.ROUTE))
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

    private companion object {
        const val CITY_SEARCH_LIMIT = 20
        const val SAVE_ERROR = "Couldn't save prayer settings. Please try again."
        const val NO_DEVICE_FIX = "No device location available right now."
        const val DEVICE_LOCATION_ERROR = "Couldn't read device location."

        // Matches com.sidr.launcher.domain.permission.PermissionFeature.PRAYER_LOCATION.name — kept
        // as a string (not the enum) so this Android-free VM never imports the permission port; the
        // education route/VM parses the nav-arg string back into the enum (Block T precedent).
        const val PRAYER_LOCATION_FEATURE = "PRAYER_LOCATION"
    }
}
