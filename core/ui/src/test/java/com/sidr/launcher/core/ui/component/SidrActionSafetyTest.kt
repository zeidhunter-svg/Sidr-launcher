package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
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
