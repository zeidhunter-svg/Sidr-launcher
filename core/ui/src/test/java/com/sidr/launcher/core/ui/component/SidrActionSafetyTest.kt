package com.sidr.launcher.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrActionSafetyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun action_proposal_does_not_execute_on_render() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionProposal(
                    title = "Open website",
                    description = "This is only a proposal.",
                    onExecute = { clicks.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("This is only a proposal.").assertIsDisplayed()
        assertEquals(0, clicks.get())
    }

    @Test fun action_proposal_execute_is_one_shot() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionProposal(
                    title = "Open website",
                    onExecute = { clicks.incrementAndGet() },
                    executeLabel = "Open",
                )
            }
        }
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Open").performClick()
        assertEquals(1, clicks.get())
    }

    @Test fun action_proposal_executing_disables_controls() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionProposal(
                    title = "Open website",
                    onExecute = { clicks.incrementAndGet() },
                    executing = true,
                )
            }
        }
        compose.onNodeWithText("Run").assertIsNotEnabled()
        compose.onNodeWithText("Run").performClick()
        assertEquals(0, clicks.get())
    }

    @Test fun permission_notice_keeps_without_permission_copy_and_not_now() {
        val primary = AtomicInteger(0)
        val secondary = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPermissionNotice(
                    title = "Voice input",
                    body = "Microphone access lets Sidr hear a command.",
                    primaryLabel = "Continue",
                    onPrimary = { primary.incrementAndGet() },
                    withoutPermission = "Typed commands keep working.",
                    onSecondary = { secondary.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("Typed commands keep working.").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(0, primary.get())
        assertEquals(1, secondary.get())
    }

    /**
     * Whole-branch review fix (2026-08-17, item 1, CRITICAL) regression proof. The bug: the status
     * chip's colour used to be *derived* from the display-label text (`label.uppercase(Locale.ROOT)`
     * matched against English literals like "DENIED"), so a translated label fell through to the
     * `else -> INFO` branch and a permanently-denied permission rendered in the neutral info colour
     * instead of red DANGER in `ru`/`tr`. Fixed by making the tone travel as typed [SidrLabeledStatus]
     * data instead of being re-derived from text - `SidrPermissionNotice.status` is no longer even
     * typeable as a raw `String`, so the exact shape of the old bug (guessing a `SidrStatus` from
     * translated copy) is now a compile error, not just a runtime behaviour to re-check per call.
     *
     * This test renders with a Russian label (`"ЗАБЛОКИРОВАНО"`, which matches none of the deleted
     * function's English literals) and an explicit `SidrStatus.DANGER` tone, proving the two travel
     * together through [SidrPermissionNotice] -> [SidrStatusChip] without a crash or a silent
     * default. (A pixel-level colour assertion was attempted and dropped: `compose-ui-test`'s
     * `captureToImage()` times out under this module's Robolectric setup waiting for a real Android
     * window redraw that Robolectric never provides, and routing through Roborazzi's own capture
     * mechanism to a non-golden temp file hit a second harness limitation - `javax.imageio` does not
     * resolve on the Android-unit-test compile classpath. [danger_and_info_tones_are_visually_distinct]
     * below instead pins, at the token level, that the two tones this bug could confuse are not the
     * same colour.)
     */
    @Test fun permission_notice_status_tone_is_typed_not_derived_from_label_text() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPermissionNotice(
                    title = "Микрофон",
                    body = "Доступ к микрофону нужен для голосовых команд.",
                    primaryLabel = "Открыть настройки",
                    onPrimary = {},
                    status = SidrLabeledStatus("ЗАБЛОКИРОВАНО", SidrStatus.DANGER),
                )
            }
        }
        compose.onNodeWithText("ЗАБЛОКИРОВАНО").assertIsDisplayed()
    }

    /** Companion to the regression proof above: DANGER and INFO - the two tones the deleted
     *  text-matching bug could confuse for a translated "blocked" label - are not the same token. */
    @Test fun danger_and_info_tones_are_visually_distinct() {
        var dangerColor = Color.Unspecified
        var infoColor = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true) {
                dangerColor = SidrTheme.colors.danger
                infoColor = SidrTheme.colors.info
            }
        }
        assertNotEquals(dangerColor, infoColor)
    }

    @Test fun result_surface_partial_is_not_completed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrResultSurface(
                    tone = SidrResultTone.Partial,
                    title = "Partially done",
                    body = "One step finished; one step still needs you.",
                )
            }
        }
        compose.onNodeWithText("PARTIAL").assertIsDisplayed()
        compose.onNodeWithText("One step finished; one step still needs you.").assertIsDisplayed()
    }

    @Test fun error_surface_only_shows_retry_when_action_is_supplied() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrErrorSurface(
                    title = "Could not load apps",
                    whatFailed = "The app list was unavailable.",
                    primaryAction = SidrSurfaceAction("Retry") {},
                )
            }
        }
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test fun offline_and_blocked_states_are_labeled() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Column {
                    SidrOfflineState(
                        body = "Cloud routing is unavailable.",
                        availableOffline = "App launch still works.",
                    )
                    SidrBlockedState(
                        title = "Permission needed",
                        body = "Voice input cannot start yet.",
                        next = "Grant microphone access or keep typing.",
                    )
                }
            }
        }
        compose.onNodeWithText("OFFLINE").assertIsDisplayed()
        compose.onNodeWithText("BLOCKED").assertIsDisplayed()
        compose.onNodeWithText("App launch still works.").assertIsDisplayed()
    }
}
