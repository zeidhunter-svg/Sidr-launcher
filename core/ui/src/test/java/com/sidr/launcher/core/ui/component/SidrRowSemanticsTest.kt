package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * DS-3 row semantics tests (spec §7: toggle row single owner, choice row radio role, navigation row
 * title/value, destructive row explicit label).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrRowSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toggle_row_title_and_description_are_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrToggleRow(
                    title = "AI suggestions",
                    checked = true,
                    onCheckedChange = {},
                    description = "Show launcher suggestions.",
                )
            }
        }
        compose.onNodeWithText("AI suggestions").assertIsDisplayed()
        compose.onNodeWithText("Show launcher suggestions.").assertIsDisplayed()
    }

    @Test fun toggle_row_fires_on_checked_change_once() {
        val changes = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrToggleRow(
                    title = "Voice input",
                    checked = false,
                    onCheckedChange = { changes.incrementAndGet() },
                )
            }
        }
        // Tapping the row toggles via the single toggleable owner.
        compose.onNodeWithText("Voice input").performClick()
        assertEquals(1, changes.get())
    }

    @Test fun choice_row_title_is_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrChoiceRow(title = "Dark", selected = true, onClick = {})
            }
        }
        compose.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test fun navigation_row_title_and_value_are_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrNavigationRow(
                    title = "AI provider",
                    onClick = {},
                    value = "OpenRouter",
                    description = "Configure your AI provider.",
                )
            }
        }
        compose.onNodeWithText("AI provider").assertIsDisplayed()
        compose.onNodeWithText("OpenRouter").assertIsDisplayed()
        compose.onNodeWithText("Configure your AI provider.").assertIsDisplayed()
    }

    @Test fun destructive_row_title_is_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrDestructiveRow(title = "Forget", onClick = {}, description = "Remove all learned data.")
            }
        }
        compose.onNodeWithText("Forget").assertIsDisplayed()
        compose.onNodeWithText("Remove all learned data.").assertIsDisplayed()
    }

    @Test fun status_row_title_and_status_label_are_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrStatusRow(
                    title = "Sync",
                    status = SidrStatus.SUCCESS,
                    statusLabel = "LOCAL",
                    value = "2m ago",
                )
            }
        }
        compose.onNodeWithText("Sync").assertIsDisplayed()
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
        compose.onNodeWithText("2m ago").assertIsDisplayed()
    }
}
