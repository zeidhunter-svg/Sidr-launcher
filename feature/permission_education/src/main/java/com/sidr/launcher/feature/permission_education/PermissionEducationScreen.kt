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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTertiaryButton
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
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
 * DS-5 restyle notes: the previous bottom `TextButton("Back")` is retired in favour of the new
 * [SidrTopBar] navigation icon (same [onBack] callback) — there is now exactly one back affordance,
 * not two. Every other branch keeps its exact real callback; only composition/styling changed.
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

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = state.rationale.title,
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
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
            SidrText(text = state.rationale.body, role = SidrTextRole.HUMAN_BODY)

            SidrSurface(tone = SidrSurfaceTone.SURFACE) {
                Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                    SidrSectionHeader("WITHOUT THIS PERMISSION")
                    SidrText(
                        text = "The launcher keeps working normally — this only affects " +
                            "${state.feature.capabilityLabel()}.",
                        role = SidrTextRole.HUMAN_BODY,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        color = SidrTheme.colors.dim,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                // Dormant feature: education only, no request flow exists yet.
                !state.requestable -> {
                    SidrText(
                        text = "Not available yet.",
                        role = SidrTextRole.HUMAN_BODY,
                        color = SidrTheme.colors.faint,
                    )
                }

                state.status == PermissionStatus.GRANTED -> {
                    SidrText(
                        text = "Enabled.",
                        role = SidrTextRole.HUMAN_BODY,
                        color = SidrTheme.colors.dim,
                    )
                    // Only wallpaper has an in-screen action; voice is used from the launcher mic.
                    if (state.feature == PermissionFeature.WALLPAPER) {
                        SidrPrimaryButton(
                            text = "Set wallpaper",
                            onClick = { context.launchWallpaperPicker() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.status == PermissionStatus.PERMANENTLY_DENIED -> {
                    SidrText(
                        text = "Permission was permanently denied. Enable it from system settings to " +
                            "use this feature. The launcher keeps working without it.",
                        role = SidrTextRole.HUMAN_BODY,
                        color = SidrTheme.colors.danger,
                    )
                    SidrPrimaryButton(
                        text = "Open settings",
                        onClick = { context.openAppSettings() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    // DENIED (incl. not-yet-requested): offer the system request.
                    if (state.status == PermissionStatus.DENIED) {
                        SidrText(
                            text = "Not enabled. The launcher works fine without it.",
                            role = SidrTextRole.HUMAN_BODY,
                            color = SidrTheme.colors.dim,
                        )
                    }
                    SidrPrimaryButton(
                        text = state.rationale.ctaLabel,
                        onClick = { androidPermission?.let { permissionLauncher.launch(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SidrText(
                    text = "NOT A SYSTEM DIALOG · YOU CHOOSE",
                    role = SidrTextRole.PROVENANCE,
                )
            }

            if (!state.dismissed && state.requestable && state.status != PermissionStatus.GRANTED) {
                SidrTertiaryButton(
                    text = "Don't show this again",
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
}

/**
 * Short, already-true capability fragment for the "without this permission" card — derived from the
 * feature itself, not a new claim (mirrors what each feature's rationale/CTA already implies).
 */
private fun PermissionFeature.capabilityLabel(): String = when (this) {
    PermissionFeature.WALLPAPER -> "setting a custom wallpaper"
    PermissionFeature.VOICE_INPUT -> "voice commands"
    PermissionFeature.CALENDAR_SUGGESTIONS -> "calendar-based suggestions"
    PermissionFeature.LOCATION_SUGGESTIONS -> "location-based suggestions"
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

/** Opens the system wallpaper picker — the wallpaper feature's reaction to being enabled. */
private fun Context.launchWallpaperPicker() {
    try {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivity(Intent.createChooser(intent, "Set wallpaper"))
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
