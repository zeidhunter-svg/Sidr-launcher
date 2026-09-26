package com.sidr.launcher.domain.model

/**
 * Pure domain model for an app that is launchable on this device.
 * No android.* types — icon loading is a UI concern, done in the Compose layer by [packageName].
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val activityName: String? = null,
)
