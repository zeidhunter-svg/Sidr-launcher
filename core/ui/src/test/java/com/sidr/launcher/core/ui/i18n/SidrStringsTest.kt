package com.sidr.launcher.core.ui.i18n

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.sidr.launcher.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrStringsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resolves_from_resources_when_no_overlay() {
        var actual = ""
        compose.setContent { actual = sidrString(R.string.ui_action_cancel) }
        assertEquals("Cancel", actual)
    }

    @Test fun overlay_wins_over_resources() {
        var actual = ""
        val overlay = SidrStringOverlay { key -> if (key == "ui_action_cancel") "OVERLAID" else null }
        compose.setContent {
            CompositionLocalProvider(LocalSidrStringOverlay provides overlay) {
                actual = sidrString(R.string.ui_action_cancel)
            }
        }
        assertEquals("OVERLAID", actual)
    }

    @Test fun overlay_miss_falls_back_to_resources() {
        var actual = ""
        val overlay = SidrStringOverlay { null }
        compose.setContent {
            CompositionLocalProvider(LocalSidrStringOverlay provides overlay) {
                actual = sidrString(R.string.ui_action_cancel)
            }
        }
        assertEquals("Cancel", actual)
    }

    @Test fun overlay_template_receives_format_arguments() {
        var actual = ""
        val overlay = SidrStringOverlay { key ->
            if (key == "ui_memory_forget_content_description") "FORGET %1\$s %2\$s" else null
        }
        compose.setContent {
            CompositionLocalProvider(LocalSidrStringOverlay provides overlay) {
                actual = sidrString(R.string.ui_memory_forget_content_description, "alias", "open bank")
            }
        }
        assertEquals("FORGET alias open bank", actual)
    }
}
