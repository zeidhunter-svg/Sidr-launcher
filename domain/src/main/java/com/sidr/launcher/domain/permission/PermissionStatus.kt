package com.sidr.launcher.domain.permission

/**
 * State of the Android permission backing a [PermissionFeature].
 *
 * - [GRANTED]            — held; the feature may run.
 * - [DENIED]             — not held, but the system request dialog may still be shown (ask again).
 * - [PERMANENTLY_DENIED] — not held and the OS will no longer show the dialog ("Don't ask again");
 *                          the only recovery is the app's system Settings screen.
 *
 * A self-check (`checkSelfPermission`) can only distinguish GRANTED vs not-held, so a
 * [com.sidr.launcher.domain.permission.PermissionChecker] returns GRANTED or DENIED only. The
 * PERMANENTLY_DENIED refinement is supplied by the request flow (the `shouldShowRequestPermissionRationale`
 * signal) in the UI layer. Kept in :domain so the status never carries an Android type.
 */
enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED }
