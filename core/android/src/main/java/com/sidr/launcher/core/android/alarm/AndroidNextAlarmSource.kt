package com.sidr.launcher.core.android.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import com.sidr.launcher.domain.alarm.NextAlarm
import com.sidr.launcher.domain.alarm.NextAlarmSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Android implementation of [NextAlarmSource] (A4′ acceptance tail, task 8; owner ruling 5: а).
 *
 * Reads [AlarmManager.getNextAlarmClock] on collection and again on every [AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED]
 * — a protected system broadcast, received by a context-registered receiver that is NOT exported (nothing but the
 * system may tell it to re-read). The receiver is registered BEFORE the first read, so a change between the two is
 * not lost; it lives exactly as long as a collector does.
 *
 * **Only the clock's own alarm** (review focus 5): any app may call `setAlarmClock` — Find My Mobile does on the
 * owner's phone — and the chip's tap opens the clock's alarm list (`show_alarms`, `AlarmClock.ACTION_SHOW_ALARMS`),
 * so a time some other app set would be a chip its own tap contradicts. The alarm counts only when the creator of its
 * show intent is the package that resolves `ACTION_SHOW_ALARMS` (`MATCH_DEFAULT_ONLY` — what `startActivity` opens);
 * anything else — another creator, no show intent, nothing resolving, a disambiguation chooser resolving instead of
 * one clock — is `null`, never a guess. The manifest's `<queries>` already declares `SHOW_ALARMS` (A1″ phase 3b), so
 * the clock is visible to [PackageManager.resolveActivity] under package-visibility rules.
 *
 * **Named limit — a sooner alarm of another app hides the clock's own.** `getNextAlarmClock()` reports only the SOONEST
 * alarm clock across every app; no public API reads «the clock's own next alarm». So when another app (Find My Mobile)
 * has an alarm clock set before the clock's, this source returns `null` and no chip shows, although the clock has a
 * later alarm — it fails closed (no chip), never showing a time that is not the clock's.
 *
 * **No permission** — neither `getNextAlarmClock`, nor the broadcast, nor the resolve needs one, so nothing here can
 * block startup; and nothing runs before the first collector (the ViewModel shares it `WhileSubscribed`).
 *
 * Plain class (no Hilt annotations, the [com.sidr.launcher.core.android.connectivity.AndroidConnectivityChecker]
 * precedent); constructed in `:app`'s `NextAlarmModule` with the application [Context]. [map] runs in the collector's
 * context — the ViewModel moves it off the main thread; the receiver itself only signals.
 */
class AndroidNextAlarmSource(
    private val context: Context,
) : NextAlarmSource {

    override fun observe(): Flow<NextAlarm?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().map { read() }.distinctUntilChanged()

    private fun read(): NextAlarm? {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return null
        val next = alarms.nextAlarmClock ?: return null
        val creator = next.showIntent?.creatorPackage ?: return null
        val clock = context.packageManager
            .resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?: return null
        return if (creator == clock) NextAlarm(next.triggerTime) else null
    }
}
