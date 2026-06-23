package com.sidr.launcher.feature.permission_education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionPrefsRepository
import com.sidr.launcher.domain.permission.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single source of truth for the permission-education screen (Block G).
 *
 * Holds no Android types: it reads permission state through the [PermissionChecker] domain port
 * and persists the "dismissed" flag through [PermissionPrefsRepository]. The system permission
 * dialog itself is launched by the screen (Android `ActivityResultContracts`), which reports the
 * result back via [onPermissionResult] — keeping "education ≠ request" (Fork 5) and the rule that
 * a ViewModel never touches Android UI APIs.
 *
 * Phase 4 targets a single live feature ([PermissionFeature.WALLPAPER]); a future slice can route
 * the feature in via a nav arg / SavedStateHandle once more features have live request flows.
 */
@HiltViewModel
class PermissionEducationViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val permissionPrefs: PermissionPrefsRepository,
) : ViewModel() {

    private val feature: PermissionFeature = PermissionFeature.WALLPAPER

    private val _uiState = MutableStateFlow(
        PermissionEducationUiState(
            feature = feature,
            status = permissionChecker.status(feature),
            rationale = rationaleFor(feature),
            requestable = feature.requestable,
            dismissed = false,
        )
    )
    val uiState: StateFlow<PermissionEducationUiState> = _uiState.asStateFlow()

    init {
        // Observe the persisted dismissed flag so the screen reflects "don't ask again" across
        // restarts. A read failure falls back to not-dismissed (the flow emits false).
        viewModelScope.launch {
            permissionPrefs.isDismissed(feature).collect { dismissed ->
                _uiState.update { it.copy(dismissed = dismissed) }
            }
        }
    }

    /**
     * Re-check the live permission status (e.g. after returning from the system Settings screen).
     *
     * Upgrade-only: a re-check may only move the status **up to [PermissionStatus.GRANTED]**; it must
     * never overwrite an existing [PermissionStatus.PERMANENTLY_DENIED] with [PermissionStatus.DENIED].
     * The [PermissionChecker] runs over `checkSelfPermission`, which can report only GRANTED/DENIED —
     * it cannot observe permanent denial (that is derivable only from the request callback's
     * `shouldShowRequestPermissionRationale`, see [onPermissionResult]). An unconditional overwrite
     * would therefore silently downgrade a permanently-denied feature back to merely-denied on every
     * refresh. This guard is a *partial* fix sized to the current SET_WALLPAPER (normal-permission)
     * scope; it must be revisited when the first dangerous permission lands (`RECORD_AUDIO`, Ph7).
     */
    fun refreshStatus() {
        val checked = permissionChecker.status(feature)
        _uiState.update { current ->
            val next = when {
                // A live grant always wins (e.g. the user granted it in system Settings).
                checked == PermissionStatus.GRANTED -> PermissionStatus.GRANTED
                // Preserve the stronger existing status: never downgrade PERMANENTLY_DENIED → DENIED.
                current.status == PermissionStatus.PERMANENTLY_DENIED -> PermissionStatus.PERMANENTLY_DENIED
                else -> checked
            }
            current.copy(status = next)
        }
    }

    /**
     * Apply the outcome of a system permission request.
     * @param granted whether the permission is now held.
     * @param canRequestAgain whether the OS will still show the dialog next time. When a denial
     *   leaves this false, the permission is permanently denied and only system Settings recovers it.
     */
    fun onPermissionResult(granted: Boolean, canRequestAgain: Boolean) {
        val status = when {
            granted -> PermissionStatus.GRANTED
            canRequestAgain -> PermissionStatus.DENIED
            else -> PermissionStatus.PERMANENTLY_DENIED
        }
        _uiState.update { it.copy(status = status) }
    }

    /** User chose "don't show this again"; persist per-feature so other features are unaffected. */
    fun onDismissForever() {
        _uiState.update { it.copy(dismissed = true) }
        viewModelScope.launch {
            try {
                permissionPrefs.setDismissed(feature, true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Persisting the dismissal is best-effort; the in-memory state already reflects it.
            }
        }
    }
}

/**
 * Screen state. [status] != [PermissionStatus.GRANTED] means exactly this feature is disabled —
 * the launcher core is a separate destination and is never affected by a denial here.
 */
data class PermissionEducationUiState(
    val feature: PermissionFeature,
    val status: PermissionStatus,
    val rationale: PermissionRationale,
    val requestable: Boolean,
    val dismissed: Boolean,
)
