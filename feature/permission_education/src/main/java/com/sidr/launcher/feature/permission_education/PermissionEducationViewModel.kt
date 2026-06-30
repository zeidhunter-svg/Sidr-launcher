package com.sidr.launcher.feature.permission_education

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.navigation.Routes
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
 * The educated feature is routed in via the [Routes.PermissionEducation.ARG_FEATURE] nav arg
 * ([SavedStateHandle]); it defaults to [PermissionFeature.WALLPAPER] when the arg is absent or
 * unparseable (Block T — replacing the former Phase-4 hardcode now that [PermissionFeature.VOICE_INPUT]
 * also has a live request flow).
 */
@HiltViewModel
class PermissionEducationViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val permissionPrefs: PermissionPrefsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Routed from the nav arg; defaults to WALLPAPER for a bare `permission_education` route or an
    // unrecognised value (forward-compatible if the enum changes).
    private val feature: PermissionFeature =
        savedStateHandle.get<String>(Routes.PermissionEducation.ARG_FEATURE)
            ?.let { name -> runCatching { PermissionFeature.valueOf(name) }.getOrNull() }
            ?: PermissionFeature.WALLPAPER

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
     * Re-check the live permission status (called on `ON_RESUME` by the screen, e.g. after returning
     * from the system Settings screen). Block-H debt **discharged** here for the dangerous
     * `RECORD_AUDIO` case (Block T).
     *
     * This is **not** a blunt "upgrade-only" rule (which would wrongly hide a real revocation). The
     * branches are disjoint by the value of `current`:
     * - **(a)** a live `GRANTED` always wins — the user enabled it in Settings.
     * - **(c)** otherwise the live read is taken verbatim, so a genuine **`GRANTED → DENIED`
     *   revocation** (dangerous permissions can be revoked in Settings while we're backgrounded) **is
     *   reflected** — we never keep believing the mic is available after the user turned it off.
     * - **(b)** the *only* suppressed transition is `PERMANENTLY_DENIED → DENIED`, and it is reachable
     *   **only when `current` is already `PERMANENTLY_DENIED`** — never from `GRANTED`. That read is not
     *   a revocation (you cannot revoke an already-denied permission); it is `checkSelfPermission`'s
     *   inability to distinguish "denied-askable" from "denied-permanent" (permanence is observable
     *   only via the request callback's `shouldShowRequestPermissionRationale`, see [onPermissionResult]).
     *   Pinning the stronger known state keeps the screen on the Settings-deep-link recovery instead of
     *   bouncing back to a dead re-request button.
     *
     * Net: revocation and the permanent-denial guard never collide — a revocation always starts from
     * `GRANTED` (branch c), the guard only pins an established `PERMANENTLY_DENIED` (branch b).
     */
    fun refreshStatus() {
        val checked = permissionChecker.status(feature)
        _uiState.update { current ->
            val next = when {
                // (a) A live grant always wins (e.g. the user granted it in system Settings).
                checked == PermissionStatus.GRANTED -> PermissionStatus.GRANTED
                // (b) Preserve an established permanent denial against an ambiguous DENIED re-read;
                //     reachable only when current is already PERMANENTLY_DENIED (never from GRANTED).
                current.status == PermissionStatus.PERMANENTLY_DENIED -> PermissionStatus.PERMANENTLY_DENIED
                // (c) Take the live read — this reflects a genuine GRANTED → DENIED revocation.
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
