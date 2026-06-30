package com.sidr.launcher.feature.permission_education

import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * Display copy for a permission-education screen. This is presentation content (the "education ≠
 * request" half of Fork 5): it explains *why* a feature wants a permission and never triggers a
 * system dialog by itself. Lives in the feature module, not in :domain.
 */
data class PermissionRationale(
    val title: String,
    val body: String,
    /** Primary call-to-action label used when the feature is requestable and not yet granted. */
    val ctaLabel: String,
)

/**
 * Static rationale per feature. Every feature — including dormant ones — has education content;
 * only requestable features ever pair it with a system request. `BIND_ACCESSIBILITY_SERVICE` has
 * no entry here by design (deferred to Phase 8).
 */
fun rationaleFor(feature: PermissionFeature): PermissionRationale = when (feature) {
    PermissionFeature.WALLPAPER -> PermissionRationale(
        title = "Set your wallpaper",
        body = "Sidr can open the wallpaper picker so you can personalise your home screen. " +
            "This is optional — the launcher works fully without it, and you can change your mind " +
            "any time.",
        ctaLabel = "Enable wallpaper",
    )

    PermissionFeature.VOICE_INPUT -> PermissionRationale(
        title = "Voice commands",
        body = "Voice input lets you speak commands instead of typing. The launcher only listens " +
            "while you tap the mic, never records in the background, and prefers on-device " +
            "recognition. It's optional — typing always works without it.",
        ctaLabel = "Enable microphone",
    )

    PermissionFeature.CALENDAR_SUGGESTIONS -> PermissionRationale(
        title = "Calendar-aware suggestions",
        body = "Reading your calendar lets Sidr suggest the right thing at the right time. It's " +
            "coming in a later release and stays off until you turn it on.",
        ctaLabel = "Coming soon",
    )

    PermissionFeature.LOCATION_SUGGESTIONS -> PermissionRationale(
        title = "Location-aware suggestions",
        body = "Using your location lets Sidr surface nearby-relevant actions. It's coming in a " +
            "later release and stays off until you turn it on.",
        ctaLabel = "Coming soon",
    )
}
