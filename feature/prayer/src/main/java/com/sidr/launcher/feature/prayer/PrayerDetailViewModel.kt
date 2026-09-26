package com.sidr.launcher.feature.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerPreferencesRepository
import com.sidr.launcher.domain.prayer.UnavailableReason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** Screen state for [PrayerDetailScreen]. [tzId] is the setup's location timezone, used to render
 *  every instant in the LOCATION's zone (spec §6) — `null` only alongside an [PrayerContext.Unavailable]
 *  context, since a `PrayerContext.Available` schedule cannot exist without a location. */
data class PrayerDetailUiState(
    val context: PrayerContext = PrayerContext.Unavailable(UnavailableReason.NOT_CONFIGURED),
    val tzId: String? = null,
)

/**
 * Prayer detail ViewModel (DS-6B Task 8). Android-free: injects only [GetPrayerContextUseCase] +
 * [PrayerPreferencesRepository] (the latter ONLY to read [com.sidr.launcher.domain.prayer.PrayerLocation.tzId]
 * for display formatting — never written here). Re-derives [PrayerContext] every time the persisted
 * setup changes (`flatMapLatest`), so returning here after editing settings shows the fresh schedule
 * without a manual refresh (the Task-7 `LearnedChoicesViewModel` `flatMapLatest` precedent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrayerDetailViewModel @Inject constructor(
    private val preferences: PrayerPreferencesRepository,
    private val getPrayerContext: GetPrayerContextUseCase,
) : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    val uiState: StateFlow<PrayerDetailUiState> = preferences.setup()
        .flatMapLatest { setup ->
            getPrayerContext.get().map { context ->
                PrayerDetailUiState(context = context, tzId = setup?.location?.tzId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PrayerDetailUiState(),
        )

    /**
     * Opens prayer setup. [section] scopes the destination to the one setting the user tapped;
     * `null` (the unconfigured-state row) opens the whole setup page.
     *
     * All three setup rows used to call this with no argument, so Method, Madhab and Location every
     * time pushed the same `prayer_settings` route and landed at the top of the page — under the
     * 11-row calculation-method list, which fills the viewport. Two of the three rows therefore
     * looked like they opened the method picker.
     */
    fun openSettings(section: PrayerSettingsSection?) {
        val route = section?.route() ?: Routes.PrayerSettings.ROUTE
        _navigationEvents.trySend(NavigationEvent.NavigateTo(route))
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }
}
