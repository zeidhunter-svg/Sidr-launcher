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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus

/**
 * Permission-education destination (Block G), replacing the old inline `Text("Permission
 * Education")` placeholder.
 *
 * Implements the "education ≠ request" split (Fork 5): the rationale text is always shown without
 * any system dialog; the dialog is launched only when the user taps the call-to-action for a
 * *requestable* feature. A denial disables exactly this feature — the launcher core is a separate
 * destination and is never blocked. The system request + wallpaper launch are the only Android
 * glue here; all decisions live in [PermissionEducationViewModel].
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = state.rationale.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = state.rationale.body, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        when {
            // Dormant feature: education only, no request flow exists yet.
            !state.requestable -> {
                Text(
                    text = "Not available yet.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            state.status == PermissionStatus.GRANTED -> {
                Text(
                    text = "Enabled.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Only wallpaper has an in-screen action; voice is used from the launcher mic.
                if (state.feature == PermissionFeature.WALLPAPER) {
                    Button(
                        onClick = { context.launchWallpaperPicker() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Set wallpaper") }
                }
            }

            state.status == PermissionStatus.PERMANENTLY_DENIED -> {
                Text(
                    text = "Permission was permanently denied. Enable it from system settings to " +
                        "use this feature. The launcher keeps working without it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { context.openAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open settings") }
            }

            else -> {
                // DENIED (incl. not-yet-requested): offer the system request.
                if (state.status == PermissionStatus.DENIED) {
                    Text(
                        text = "Not enabled. The launcher works fine without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Button(
                    onClick = { androidPermission?.let { permissionLauncher.launch(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.rationale.ctaLabel) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.dismissed && state.requestable && state.status != PermissionStatus.GRANTED) {
            TextButton(
                onClick = viewModel::onDismissForever,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Don't show this again") }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Back") }
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
