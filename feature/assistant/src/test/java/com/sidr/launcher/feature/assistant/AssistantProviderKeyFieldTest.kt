package com.sidr.launcher.feature.assistant

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4' tail task 10 (owner ruling 9): on the phone the provider-key field sat under the soft keyboard.
 * The panel asks to be brought into view when the key field takes focus; the ask goes through an
 * injectable callback because `BringIntoViewRequester` is a sealed interface (foundation 1.12.0) and
 * cannot be faked. That the field actually rises above the real IME is not provable here — it is a
 * line of the acceptance checklist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantProviderKeyFieldTest {
    @get:Rule val compose = createComposeRule()

    private fun fieldLabel(key: String) = AssistantStrings.en(key)

    @Test fun `focusing the key field asks to be brought into view`() {
        var asked = 0
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AssistantProviderPanel(
                    form = ProviderFormState(),
                    onSave = { _, _, _ -> },
                    onKeyFieldFocused = { asked++ },
                )
            }
        }
        assertEquals(0, asked)

        compose.onNodeWithContentDescription(fieldLabel("assistant_field_api_key")).performSemanticsAction(SemanticsActions.RequestFocus)

        compose.waitForIdle()
        assertEquals(1, asked)
    }

    @Test fun `focusing another field does not ask for the key field`() {
        var asked = 0
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AssistantProviderPanel(
                    form = ProviderFormState(),
                    onSave = { _, _, _ -> },
                    onKeyFieldFocused = { asked++ },
                )
            }
        }

        compose.onNodeWithContentDescription(fieldLabel("assistant_field_model")).performSemanticsAction(SemanticsActions.RequestFocus)

        compose.waitForIdle()
        assertEquals(0, asked)
    }
}
