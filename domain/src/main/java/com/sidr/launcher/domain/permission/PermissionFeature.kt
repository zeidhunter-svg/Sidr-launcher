package com.sidr.launcher.domain.permission

/**
 * Optional, user-initiated features that may require an Android runtime permission (Block G).
 *
 * Scope (Fork 5): only [WALLPAPER] has a LIVE request flow in Phase 4. The other entries are
 * DORMANT — their education/rationale may be displayed, but the system permission dialog is NOT
 * wired until their owning phase (voice → Ph7, calendar/location → Ph7).
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

    // Dormant — education content may exist, the request flow stays off until the listed phase.
    VOICE_INPUT(requestable = false),
    CALENDAR_SUGGESTIONS(requestable = false),
    LOCATION_SUGGESTIONS(requestable = false),
}
