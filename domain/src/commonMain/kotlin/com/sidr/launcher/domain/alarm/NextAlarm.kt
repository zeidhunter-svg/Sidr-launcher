package com.sidr.launcher.domain.alarm

import kotlinx.coroutines.flow.Flow

/**
 * The next alarm the user's CLOCK app has set (A4′ acceptance tail, task 8; owner ruling 5: а) — what the Home date
 * line's chip shows. [triggerEpochMs] is the wall-clock instant it rings, in milliseconds since the epoch; formatting
 * it is the feature layer's (the device's 12/24-hour setting), never this type's.
 */
data class NextAlarm(val triggerEpochMs: Long)

/**
 * The port behind the chip. [observe] emits the current next alarm on collection and again whenever it changes; `null`
 * when there is none — and when the next alarm clock on the device was set by an app that is not the clock (on the
 * owner's phone Find My Mobile sets one): the chip is the clock's, and a time the clock's alarm list does not show
 * would be a chip whose tap contradicts it. `:core:android` implements it; nothing here needs a permission.
 */
interface NextAlarmSource {
    fun observe(): Flow<NextAlarm?>
}
