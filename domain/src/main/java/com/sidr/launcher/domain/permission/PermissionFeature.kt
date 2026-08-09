package com.sidr.launcher.domain.permission

/**
 * Optional, user-initiated features that may require an Android runtime permission (Block G).
 *
 * Scope (Fork 5): [WALLPAPER] has had a LIVE request flow since Phase 4; [VOICE_INPUT] joined it in
 * Phase 7 (Block T — `RECORD_AUDIO`, the first *dangerous* live request); [CALENDAR_SUGGESTIONS] and
 * [LOCATION_SUGGESTIONS] join it in Block U; [PRAYER_LOCATION] joins it in DS-6B Task 8.
 *
 * **[PRAYER_LOCATION] is deliberately a SEPARATE feature from [LOCATION_SUGGESTIONS]**, even though
 * both back `ACCESS_FINE_LOCATION` — a documented deviation from the DS-6B plan's "no new permission
 * feature" line. [LOCATION_SUGGESTIONS]' rationale copy is suggestion-specific ("suggesting a maps
 * app when you're out and about") and would be factually MISLEADING if reused for a prayer-location
 * request (the location is used to compute prayer times, never to suggest a maps app). Honest,
 * feature-specific copy beats a reused-but-misleading generic "LOCATION" feature; the underlying
 * request-flow template ([PermissionEducationScreen]/[PermissionEducationViewModel]) is reused
 * verbatim, only the enum entry + its rationale/label copy are new.
 *
 * `BIND_ACCESSIBILITY_SERVICE` is intentionally ABSENT and must not be added here: accessibility
 * is deferred to Phase 8 and requires its own explicit user-initiated consent flow. Listing it —
 * even as education copy — would imply consent before that flow exists.
 *
 * [requestable] gates whether a system permission request may be launched for this feature now.
 * A dormant feature must never reach the request contract: education only, no dialog.
 *
 * This type is pure (no Android imports). The mapping from a feature to its concrete manifest
 * permission string lives in the Android layer (`core/android`), not here.
 */
enum class PermissionFeature(val requestable: Boolean) {
    WALLPAPER(requestable = true),

    // Live since Phase 7 Block T — the mic affordance launches the RECORD_AUDIO request flow.
    VOICE_INPUT(requestable = true),

    // Live since Phase 7 Block U — the opt-in suggestion providers trigger these request flows.
    CALENDAR_SUGGESTIONS(requestable = true),
    LOCATION_SUGGESTIONS(requestable = true),

    // DS-6B Task 8 — the prayer "Use device location" button. Same manifest permission as
    // LOCATION_SUGGESTIONS, but a distinct feature with honest, prayer-specific rationale copy.
    PRAYER_LOCATION(requestable = true),
}
