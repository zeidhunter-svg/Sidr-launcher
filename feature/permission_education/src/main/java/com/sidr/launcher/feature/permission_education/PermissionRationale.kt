package com.sidr.launcher.feature.permission_education

import androidx.annotation.StringRes
import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * I18N-1 Task 6: resolves the consent-copy sentence explaining *why* a feature wants a permission
 * (the "education ≠ request" half of Fork 5). This is Class-B locked vocabulary (spec §7.2) —
 * translated like any other string, but living in `strings_locked.xml` so a change to consent copy is
 * conspicuous and lands in owner review (Task 15's sign-off package). Screen chrome (title,
 * call-to-action label) is resolved separately, from ordinary (unlocked) string resources, directly in
 * [PermissionEducationScreen].
 *
 * Kept a `when` with NO `else` branch deliberately — the existing file's contract: a newly added
 * [PermissionFeature] without a rationale entry here is a compile error, not a silently blank consent
 * screen. `BIND_ACCESSIBILITY_SERVICE` has no entry here by design (deferred to Phase 8).
 */
@StringRes
internal fun rationaleFor(feature: PermissionFeature): Int = when (feature) {
    PermissionFeature.WALLPAPER -> R.string.perm_rationale_wallpaper
    PermissionFeature.VOICE_INPUT -> R.string.perm_rationale_voice_input
    PermissionFeature.CALENDAR_SUGGESTIONS -> R.string.perm_rationale_calendar
    PermissionFeature.LOCATION_SUGGESTIONS -> R.string.perm_rationale_location
    // DS-6B Task 8 — deliberately separate from LOCATION_SUGGESTIONS' copy above: that rationale is
    // suggestion-specific ("suggesting a maps app") and would be misleading here, where the location
    // is used only to compute prayer times.
    PermissionFeature.PRAYER_LOCATION -> R.string.perm_rationale_prayer_location
}
