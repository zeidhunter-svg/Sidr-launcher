package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrMemorySemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun memory_item_exposes_status_evidence_and_forget_action() {
        val forgets = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrMemoryItem(
                    title = "open bank",
                    value = "Turkiye Finans",
                    type = SidrMemoryType.LearnedPreference,
                    status = SidrMemoryStatus.Active,
                    evidence = "Based on confirmed choices",
                    provenance = "Learned from ambiguous launches",
                    onForget = { forgets.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithText("LEARNED PREFERENCE").assertIsDisplayed()
        compose.onNodeWithText("\"open bank\" -> Turkiye Finans").assertIsDisplayed()
        compose.onNodeWithText("ACTIVE").assertIsDisplayed()
        compose
            .onNodeWithContentDescription(
                "source MEMORY, BASED ON CONFIRMED CHOICES, LEARNED FROM AMBIGUOUS LAUNCHES, LOCAL ONLY",
            )
            .assertIsDisplayed()

        compose.onNodeWithText("Forget").performClick()
        assertEquals(1, forgets.get())
    }

    @Test fun memory_disclosure_exposes_copy_and_actions() {
        val views = AtomicInteger(0)
        val forgets = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrMemoryDisclosure(
                    title = "New learned choice",
                    description = "\"open bank\" now prefers Turkiye Finans.",
                    evidence = "Based on confirmed choices",
                    provenance = "Local only",
                    onView = { views.incrementAndGet() },
                    onForget = { forgets.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithText("New learned choice").assertIsDisplayed()
        compose.onNodeWithText("\"open bank\" now prefers Turkiye Finans.").assertIsDisplayed()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Forget").performClick()

        assertEquals(1, views.get())
        assertEquals(1, forgets.get())
    }

    @Test fun forget_gate_cancel_and_forget_are_explicit() {
        val cancels = AtomicInteger(0)
        val forgets = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrForgetGate(
                    title = "Forget learned choice?",
                    consequence = "\"open bank\" will no longer prefer Turkiye Finans.",
                    evidence = "The next ambiguous request will ask you to choose again.",
                    onCancel = { cancels.incrementAndGet() },
                    onForget = { forgets.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithText("Forget learned choice?").assertIsDisplayed()
        compose.onNodeWithText("\"open bank\" will no longer prefer Turkiye Finans.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, cancels.get())
        assertEquals(0, forgets.get())
    }
}
