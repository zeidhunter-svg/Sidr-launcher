package com.sidr.launcher.data.repository.agent

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [ContextPermissionPresence] is the **only production truth in the whole permission gate**, and until
 * this file it had no test at all (fix round 1, finding I3). Every other test in this family injects a
 * fake presence, and both `:app` guards grant everything by construction — deliberately, so they stay
 * strict — so nothing anywhere exercised the one method that actually asks Android.
 *
 * **Its dangerous drift direction is the silent one.** A slip toward always-`true` makes the source
 * filter (spec §7.7 item 2) and the worker precondition (item 3) inert while every test in the repo
 * stays green: a fake-driven suite cannot notice that the real implementation stopped discriminating.
 * The opposite drift — always-`false` — is loud, because the tools would simply stop being registered.
 * So the test that matters is the pair below: the same permission string read on a granted build and
 * on a denied one, asserting the two answers differ in the right direction.
 *
 * That is the shape measurement row 33 used on the device — `checkSelfPermission` answered
 * `GRANTED(0)` on the declaring build and `DENIED(-1)` without it, discriminating the two builds
 * exactly. Robolectric's shadow is the same discrimination without a phone; it is not a substitute for
 * row 33, which is what established that the platform behaves this way at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ContextPermissionPresenceTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a granted permission reads as held`() {
        shadowOf(app).grantPermissions(SET_ALARM)

        assertEquals(true, ContextPermissionPresence(app).isGranted(SET_ALARM))
    }

    @Test
    fun `a permission the process does not hold reads as absent`() {
        shadowOf(app).denyPermissions(SET_ALARM)

        assertEquals(
            "If this ever answers true for a permission the process does not hold, the source filter " +
                "and the worker precondition both become inert and NOTHING else in the repo goes red " +
                "— every other test in this family injects a fake presence.",
            false,
            ContextPermissionPresence(app).isGranted(SET_ALARM),
        )
    }

    private companion object {
        /** The real string `set_timer` is gated on, not a synthetic one — the row 27 permission. */
        const val SET_ALARM = "com.android.alarm.permission.SET_ALARM"
    }
}
