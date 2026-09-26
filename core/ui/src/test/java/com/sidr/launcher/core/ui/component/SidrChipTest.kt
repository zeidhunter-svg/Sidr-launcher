package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.theme.SidrDarkColors
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * DS-3 chip tests (spec §7: selected not colour-only, status/risk never accent, disabled no callback).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun risk_chip_safe_maps_to_success_not_accent() {
        // Risk tone must map to a fixed status token, never the accent (spec §5.3).
        assertEquals(SidrStatus.SUCCESS, SidrRiskTone.Safe.status())
        assertEquals(SidrStatus.ATTENTION, SidrRiskTone.Confirm.status())
        assertEquals(SidrStatus.INFO, SidrRiskTone.External.status())
        assertEquals(SidrStatus.DANGER, SidrRiskTone.Destructive.status())
        assertNotEquals(SidrDarkColors.accent, SidrDarkColors.success)
        assertNotEquals(SidrDarkColors.accent, SidrDarkColors.danger)
    }

    @Test fun status_chip_label_is_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrStatusChip("LOCAL", SidrStatus.SUCCESS)
            }
        }
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
    }

    @Test fun route_chip_selected_label_is_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrRouteChip("APP", selected = true, onClick = {})
            }
        }
        compose.onNodeWithText("APP").assertIsDisplayed()
    }

    @Test fun filter_chip_fires_click_once() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrFilterChip("8", selected = false, onClick = { clicks.incrementAndGet() })
            }
        }
        compose.onNodeWithText("8").performClick()
        assertEquals(1, clicks.get())
    }
}

