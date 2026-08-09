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
        body = "Reading your calendar lets Sidr suggest the right thing at the right time, like " +
            "nudging you toward an upcoming event. Sidr only checks whether something is coming up " +
            "soon — it never stores event titles, times, or details. It's optional — suggestions " +
            "work fine without it, and you can change your mind any time.",
        ctaLabel = "Enable calendar suggestions",
    )

    PermissionFeature.LOCATION_SUGGESTIONS -> PermissionRationale(
        title = "Location-aware suggestions",
        body = "Using your location lets Sidr surface nearby-relevant actions, like suggesting a " +
            "maps app when you're out and about. Sidr only checks whether a recent location fix " +
            "exists — it never stores or sends your coordinates. It's optional — suggestions work " +
            "fine without it, and you can change your mind any time.",
        ctaLabel = "Enable location suggestions",
    )

    // DS-6B Task 8 — deliberately separate from LOCATION_SUGGESTIONS' copy above: that rationale is
    // suggestion-specific ("suggesting a maps app") and would be misleading here, where the location
    // is used only to compute prayer times.
    PermissionFeature.PRAYER_LOCATION -> PermissionRationale(
        title = "Prayer location",
        body = "Using your device location lets Sidr compute accurate prayer times for where you " +
            "are, rounded to about a kilometre before it's ever stored. Your coordinates stay on " +
            "this device — they are never sent anywhere or logged. It's entirely optional: picking " +
            "a city from the list works without this permission, and you can change your mind any " +
            "time.",
        ctaLabel = "Enable device location",
    )
}
