package com.sidr.launcher.domain.permission

/**
 * Optional, user-initiated features that may require an Android runtime permission (Block G).
 *
 * Scope (Fork 5): [WALLPAPER] has had a LIVE request flow since Phase 4; [VOICE_INPUT] joined it in
 * Phase 7 (Block T — `RECORD_AUDIO`, the first *dangerous* live request); [CALENDAR_SUGGESTIONS] and
 * [LOCATION_SUGGESTIONS] join it in Block U.
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
}
