package com.sidr.launcher.core.android.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.domain.alarm.NextAlarm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * A4′ acceptance tail, task 8 (owner ruling 5: а). The chip shows the CLOCK's next alarm and nothing else: the creator
 * of the alarm's show intent must be the package that opens `AlarmClock.ACTION_SHOW_ALARMS` — the alarm list the
 * chip's tap opens. Driven through the real `AlarmManager` / `PackageManager` / broadcast APIs on Robolectric's shadows;
 * `ShadowPendingIntent.setCreatorPackage` is what lets a test say who set the alarm (review focus 5: Find My Mobile,
 * `com.samsung.android.fmm`, sets alarm clocks on the owner's phone).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNextAlarmSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val source = AndroidNextAlarmSource(context)
    private var requestCode = 0

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun clockOpensTheAlarmList() {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = CLOCK
                name = "$CLOCK.AlarmListActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS), info)
    }

    /**
     * An alarm clock at [trigger] whose show intent [creator] made — `null` for an alarm with no show intent. ONE
     * operation for every call, so a second call MOVES the alarm (AlarmManager replaces an alarm by its operation)
     * rather than adding a later one that `getNextAlarmClock` would never report.
     */
    private fun setAlarm(trigger: Long, creator: String?) {
        val show = creator?.let {
            PendingIntent.getActivity(
                context,
                ++requestCode,
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                PendingIntent.FLAG_IMMUTABLE,
            ).also { pending -> shadowOf(pending).setCreatorPackage(it) }
        }
        val operation = PendingIntent.getBroadcast(context, 0, Intent("fixture.RING"), PendingIntent.FLAG_IMMUTABLE)
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), operation)
    }

    @Test
    fun `the clock's own alarm is shown`() = runTest {
        clockOpensTheAlarmList()
        setAlarm(T1, creator = CLOCK)

        assertEquals(NextAlarm(T1), source.observe().first())
    }

    @Test
    fun `an alarm set by another app is not`() = runTest {
        clockOpensTheAlarmList()
        setAlarm(T1, creator = FIND_MY_MOBILE)

        assertNull("an alarm clock Find My Mobile set is not the clock's — no chip", source.observe().first())
    }

    @Test
    fun `no alarm - null`() = runTest {
        clockOpensTheAlarmList()

        assertNull(source.observe().first())
    }

    @Test
    fun `an alarm with no show intent is not shown`() = runTest {
        clockOpensTheAlarmList()
        setAlarm(T1, creator = null)

        assertNull("nothing says who set it — not shown as the clock's", source.observe().first())
    }

    @Test
    fun `a device where nothing opens the alarm list shows nothing`() = runTest {
        setAlarm(T1, creator = CLOCK)

        assertNull("no clock to compare with — the chip's tap would open nothing", source.observe().first())
    }

    @Test
    fun `a change broadcast re-reads`() = runTest {
        clockOpensTheAlarmList()
        setAlarm(T1, creator = CLOCK)
        val seen = mutableListOf<NextAlarm?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect { seen += it } }

        setAlarm(T2, creator = CLOCK)
        context.sendBroadcast(Intent(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(NextAlarm(T1), NextAlarm(T2)), seen)
    }

    private companion object {
        const val CLOCK = "com.sec.android.app.clockpackage"
        const val FIND_MY_MOBILE = "com.samsung.android.fmm"
        const val T1 = 1_790_000_000_000L
        const val T2 = 1_790_000_600_000L
    }
}
