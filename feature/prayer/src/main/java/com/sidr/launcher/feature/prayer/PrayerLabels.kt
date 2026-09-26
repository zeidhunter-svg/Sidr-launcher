package com.sidr.launcher.feature.prayer

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.domain.prayer.CalculationMethodId

/**
 * Single source of truth for calculation-method display labels, shared by [PrayerSettingsScreen] and
 * [PrayerDetailScreen] (Task 7 fix-round — was duplicated per-screen; a reviewer flagged the
 * duplication as a second copy to drift). Both screens resolve to these two functions automatically
 * (same package, no import needed) rather than each carrying its own `when`.
 *
 * [CalculationMethodId] wraps a plain `String` key (non-sealed value class), so [methodLabelResId]'s
 * `when` can never be made compiler-exhaustive — Kotlin cannot warn if a 12th
 * [com.sidr.launcher.domain.prayer.SupportedPrayerMethods.ALL] entry ships without a matching branch
 * here. [methodLabelResId] is deliberately split out as a plain (non-`@Composable`) function — not
 * folded into [methodLabel] — specifically so `MethodLabelCoverageTest` can call it directly from a
 * plain JVM test (no Robolectric/Compose test rule) and assert it covers every catalog entry. That
 * test is the anti-drift guard this KDoc promises: without it, a newly-added method would silently
 * fall through to [methodLabel]'s `?:` fallback and render as its raw, untranslated key on the block's
 * most terminology-sensitive surface.
 */
@StringRes
internal fun methodLabelResId(methodId: CalculationMethodId): Int? = when (methodId.key) {
    "MWL" -> R.string.prayer_method_mwl
    "EGYPTIAN" -> R.string.prayer_method_egyptian
    "KARACHI" -> R.string.prayer_method_karachi
    "UMM_AL_QURA" -> R.string.prayer_method_umm_al_qura
    "DUBAI" -> R.string.prayer_method_dubai
    "MOON_SIGHTING_COMMITTEE" -> R.string.prayer_method_moon_sighting_committee
    "NORTH_AMERICA" -> R.string.prayer_method_north_america
    "KUWAIT" -> R.string.prayer_method_kuwait
    "QATAR" -> R.string.prayer_method_qatar
    "SINGAPORE" -> R.string.prayer_method_singapore
    "TURKEY" -> R.string.prayer_method_turkey
    else -> null
}

/** Never blank, never a crash: falls back to the raw key if the catalog is ever out of sync with
 *  [methodLabelResId] (same defensive shape the pre-fix-round duplicated functions had). */
@Composable
internal fun methodLabel(methodId: CalculationMethodId): String =
    methodLabelResId(methodId)?.let { sidrString(it) } ?: methodId.key
