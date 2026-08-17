package com.sidr.launcher.feature.permission_education

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrLabeledStatus
import com.sidr.launcher.core.ui.component.SidrPermissionNotice
import com.sidr.launcher.core.ui.component.SidrPrivacyNotice
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrTertiaryButton
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus

/**
 * Permission-education destination (Block G), restyled to the SIDR design system (DS-5 track,
 * Task 6). Implements the "education ≠ request" split (Fork 5): the rationale text is always shown
 * without any system dialog; the dialog is launched only when the user taps the call-to-action for
 * a *requestable* feature. A denial disables exactly this feature — the launcher core is a separate
 * destination and is never blocked. The system request + wallpaper launch are the only Android
 * glue here; all decisions live in [PermissionEducationViewModel].
 *
 * DS-5 restyle notes: permission states render through [SidrPermissionNotice], with Back and "Not now"
 * both resolving to the same safe [onBack] callback. Every branch keeps its exact real callback; only
 * composition/styling changed.
 */
@Composable
fun PermissionEducationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: PermissionEducationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val androidPermission = state.feature.androidPermission()

    // Re-check the live status whenever the screen resumes — this is what makes the Settings
    // round-trip work for the dangerous RECORD_AUDIO case (Block T): a grant *or* a revocation done
    // in system Settings is reflected on return (the VM reconciles, see refreshStatus()).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The system permission dialog. For SET_WALLPAPER (a normal permission) the OS grants without
    // showing UI; for the dangerous RECORD_AUDIO this is the real dialog. After a denial we read
    // shouldShowRequestPermissionRationale to distinguish "ask again" from "permanently denied".
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val canRequestAgain = context.findActivity()?.let { activity ->
            androidPermission?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            } ?: true
        } ?: true
        viewModel.onPermissionResult(granted = granted, canRequestAgain = canRequestAgain)
        // Feature-specific post-grant action (only wallpaper opens a picker; voice just enables the
        // mic affordance back on the launcher).
        if (granted && state.feature == PermissionFeature.WALLPAPER) context.launchWallpaperPicker()
    }

    val title = sidrString(state.feature.titleRes())
    val body = sidrString(state.rationaleRes)

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = title,
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = sidrString(R.string.perm_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val withoutPermission = sidrString(
                R.string.perm_without_permission_template,
                sidrString(state.feature.capabilityLabelRes()),
            )

            when {
                // Dormant feature: education only, no request flow exists yet.
                !state.requestable -> {
                    SidrPrivacyNotice(
                        title = title,
                        body = body,
                        provenance = {
                            SidrText(text = sidrString(R.string.perm_not_available_yet), role = SidrTextRole.PROVENANCE)
                        },
                    )
                }

                state.status == PermissionStatus.GRANTED -> {
                    val setWallpaperLabel = sidrString(R.string.perm_set_wallpaper)
                    SidrPermissionNotice(
                        title = title,
                        body = body,
                        primaryLabel = if (state.feature == PermissionFeature.WALLPAPER) setWallpaperLabel else sidrString(R.string.perm_done),
                        onPrimary = {
                            if (state.feature == PermissionFeature.WALLPAPER) {
                                context.launchWallpaperPicker()
                            } else {
                                onBack()
                            }
                        },
                        withoutPermission = withoutPermission,
                        status = SidrLabeledStatus(sidrString(R.string.perm_status_enabled), SidrStatus.SUCCESS),
                    )
                }

                state.status == PermissionStatus.PERMANENTLY_DENIED -> {
                    SidrPermissionNotice(
                        title = title,
                        body = sidrString(R.string.perm_permanently_denied_body),
                        primaryLabel = sidrString(R.string.perm_open_settings),
                        onPrimary = { context.openAppSettings() },
                        withoutPermission = withoutPermission,
                        secondaryLabel = sidrString(R.string.perm_not_now),
                        onSecondary = onBack,
                        status = SidrLabeledStatus(sidrString(R.string.perm_status_blocked), SidrStatus.DANGER),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    // DENIED (incl. not-yet-requested): offer the system request.
                    SidrPermissionNotice(
                        title = title,
                        body = body,
                        primaryLabel = sidrString(state.feature.ctaLabelRes()),
                        onPrimary = { androidPermission?.let { permissionLauncher.launch(it) } },
                        withoutPermission = withoutPermission,
                        secondaryLabel = sidrString(R.string.perm_not_now),
                        onSecondary = onBack,
                        status = SidrLabeledStatus(sidrString(R.string.perm_status_optional), SidrStatus.INFO),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SidrText(
                text = sidrString(R.string.perm_not_a_system_dialog),
                role = SidrTextRole.PROVENANCE,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.dismissed && state.requestable && state.status != PermissionStatus.GRANTED) {
                SidrTertiaryButton(
                    text = sidrString(R.string.perm_dont_show_again),
                    onClick = viewModel::onDismissForever,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Android glue ────────────────────────────────────────────────────────────

/**
 * The manifest permission backing each feature, for the request contract. This mirrors the canonical
 * map in `core/android`'s `AndroidPermissionChecker.manifestPermission`; it is duplicated here because
 * a `feature` module must not depend on `core/android` (the screen is already the Android UI-glue
 * layer, so the string constants are local to it). Keep the two in sync. Returns null for a feature
 * with no live request flow.
 */
private fun PermissionFeature.androidPermission(): String? = when (this) {
    PermissionFeature.WALLPAPER -> Manifest.permission.SET_WALLPAPER
    PermissionFeature.VOICE_INPUT -> Manifest.permission.RECORD_AUDIO
    PermissionFeature.CALENDAR_SUGGESTIONS -> Manifest.permission.READ_CALENDAR
    PermissionFeature.LOCATION_SUGGESTIONS -> Manifest.permission.ACCESS_FINE_LOCATION
    PermissionFeature.PRAYER_LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
}

/**
 * Short, already-true capability fragment for the "without this permission" card — derived from the
 * feature itself, not a new claim (mirrors what each feature's rationale/CTA already implies).
 */
@StringRes
private fun PermissionFeature.capabilityLabelRes(): Int = when (this) {
    PermissionFeature.WALLPAPER -> R.string.perm_capability_wallpaper
    PermissionFeature.VOICE_INPUT -> R.string.perm_capability_voice_input
    PermissionFeature.CALENDAR_SUGGESTIONS -> R.string.perm_capability_calendar
    PermissionFeature.LOCATION_SUGGESTIONS -> R.string.perm_capability_location
    PermissionFeature.PRAYER_LOCATION -> R.string.perm_capability_prayer_location
}

/** Screen title per feature — screen chrome, resolved directly here (not part of the locked rationale). */
@StringRes
private fun PermissionFeature.titleRes(): Int = when (this) {
    PermissionFeature.WALLPAPER -> R.string.perm_title_wallpaper
    PermissionFeature.VOICE_INPUT -> R.string.perm_title_voice_input
    PermissionFeature.CALENDAR_SUGGESTIONS -> R.string.perm_title_calendar
    PermissionFeature.LOCATION_SUGGESTIONS -> R.string.perm_title_location
    PermissionFeature.PRAYER_LOCATION -> R.string.perm_title_prayer_location
}

/** Primary call-to-action label per feature, used when requestable and not yet granted. */
@StringRes
private fun PermissionFeature.ctaLabelRes(): Int = when (this) {
    PermissionFeature.WALLPAPER -> R.string.perm_cta_wallpaper
    PermissionFeature.VOICE_INPUT -> R.string.perm_cta_voice_input
    PermissionFeature.CALENDAR_SUGGESTIONS -> R.string.perm_cta_calendar
    PermissionFeature.LOCATION_SUGGESTIONS -> R.string.perm_cta_location
    PermissionFeature.PRAYER_LOCATION -> R.string.perm_cta_prayer_location
}

/** Unwraps the Activity from a (possibly wrapped) Context; null if none in the chain. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Opens the system wallpaper picker — the wallpaper feature's reaction to being enabled. Not a
 * `@Composable` (it runs from a click callback), so it resolves the chooser title via the plain
 * Android `Context.getString` rather than `sidrString` — the I18N-1 overlay seam is Compose-only.
 */
private fun Context.launchWallpaperPicker() {
    try {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivity(Intent.createChooser(intent, getString(R.string.perm_set_wallpaper)))
    } catch (_: ActivityNotFoundException) {
        // No wallpaper picker on this device — fail silently; nothing in the launcher breaks.
    }
}

/** Opens this app's system settings page so the user can flip a permanently-denied permission. */
private fun Context.openAppSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: ActivityNotFoundException) {
        // Settings unavailable — fail silently.
    }
}
