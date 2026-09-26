package com.sidr.launcher.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.sidr.launcher.core.ui.i18n.sidrString

/**
 * I18N-1 Task 8. [ThemeOption.ALL]/[AccentOption.ALL] (`SettingsUiState.kt`) still carry their
 * original hardcoded-English second component — that file is not part of this task's scope (not in
 * the brief's "Files: Modify" list, and its `SYSTEM`/`LIGHT`/`DARK`/`GREY`/`GREEN`/`AMBER` value
 * constants are asserted directly by `SettingsViewModelTest`). [SettingsScreen] resolves the persisted
 * `value` key through these functions instead of reading the pair's stale label, mirroring
 * `feature/prayer`'s `PrayerLabels.kt` precedent (Task 7) for a domain/state-owned enumeration whose
 * *display* text belongs in the presentation layer.
 *
 * Split into a plain (non-`@Composable`) `*ResId` function + a thin `@Composable` wrapper, same shape
 * as `PrayerLabels.kt`, so a plain JVM test could assert coverage later without a Compose test rule.
 * Falls back to the raw value key (never blank, never crash) if a value is ever added to `ThemeOption`/
 * `AccentOption` without a matching branch here.
 */
@StringRes
internal fun themeLabelResId(value: String): Int? = when (value) {
    ThemeOption.SYSTEM -> R.string.settings_theme_system
    ThemeOption.LIGHT -> R.string.settings_theme_light
    ThemeOption.DARK -> R.string.settings_theme_dark
    else -> null
}

@Composable
internal fun themeLabel(value: String): String =
    themeLabelResId(value)?.let { sidrString(it) } ?: value

@StringRes
internal fun accentLabelResId(value: String): Int? = when (value) {
    AccentOption.GREY -> R.string.settings_accent_grey
    AccentOption.GREEN -> R.string.settings_accent_green
    AccentOption.AMBER -> R.string.settings_accent_amber
    else -> null
}

@Composable
internal fun accentLabel(value: String): String =
    accentLabelResId(value)?.let { sidrString(it) } ?: value
