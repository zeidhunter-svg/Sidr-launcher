package com.sidr.launcher.domain.prayer

import com.sidr.launcher.domain.result.OperationResult

/**
 * Port: optional one-shot device location (DS-6B Task 3; Android impl in `:core:android`, Task 8 —
 * the Block-T `SpeechInputSource` precedent). Never continuous tracking (spec §0.4).
 *
 * Returned coordinates are ALREADY rounded to 2 decimal places by the implementation, before they
 * cross into domain — a precise coordinate never exists on this side of the port (spec §0.5).
 * `Success(null)` = no fix available; permission problems and read errors are
 * [OperationResult.Failure] values (denial never breaks the launcher — the city path stays primary).
 * Result carries `source = DEVICE`.
 */
interface PrayerLocationProvider {
    suspend fun currentLocation(): OperationResult<PrayerLocation?>
}
