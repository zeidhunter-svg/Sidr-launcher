package com.sidr.launcher.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4' tail task 11 (owner ruling 10, K6): the learned aliases sit on their own tab ("My aliases"), the
 * form and the app list on the other ("Add"). The tab opens on the aliases when there are any. "Forget"
 * still goes through `SidrForgetGate` (`DOC-AML-5`): nothing is deleted before its confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AliasesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val notes = AliasMemoryUiModel(
        stableId = "zametki",
        phrase = "zametki",
        targetLabel = "Notes",
        targetPackageName = "com.example.notes",
        status = SidrMemoryStatus.Active,
        evidence = AliasEvidence.UserDeclared,
        provenance = AliasProvenance.Settings,
    )
    private val apps = listOf(
        AliasPickerAppUiModel("com.example.notes", "Notes"),
        AliasPickerAppUiModel("com.example.maps", "Maps"),
    )

    private fun show(
        aliases: List<AliasMemoryUiModel>,
        onDelete: (String) -> Unit = {},
    ) {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AliasesContent(
                    uiState = AliasesUiState(aliases = aliases, pickerApps = apps, isLoading = false),
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _ -> },
                    onDelete = onDelete,
                )
            }
        }
    }

    @Test fun `with aliases the screen opens on them, not on the app list`() {
        show(listOf(notes))

        compose.onNodeWithText("zametki", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.settings_alias_phrase_placeholder)).assertCountEquals(0)
        compose.onAllNodesWithText("Maps").assertCountEquals(0)
        compose.onNodeWithText(str(R.string.settings_aliases_tab_mine)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.settings_aliases_tab_add)).assertIsDisplayed()
    }

    @Test fun `with none it opens on Add`() {
        show(emptyList())

        compose.onNodeWithText(str(R.string.settings_alias_phrase_placeholder)).assertIsDisplayed()
        compose.onNodeWithText("Maps").assertIsDisplayed()
    }

    @Test fun `the chips switch between the two tabs`() {
        show(listOf(notes))

        compose.onNodeWithText(str(R.string.settings_aliases_tab_add)).performClick()
        compose.onNodeWithText(str(R.string.settings_alias_phrase_placeholder)).assertIsDisplayed()
        compose.onAllNodesWithText("zametki", substring = true).assertCountEquals(0)

        compose.onNodeWithText(str(R.string.settings_aliases_tab_mine)).performClick()
        compose.onNodeWithText("zametki", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.settings_alias_phrase_placeholder)).assertCountEquals(0)
    }

    @Test fun `Forget still goes through the forget gate before anything is deleted`() {
        val deleted = mutableListOf<String>()
        show(listOf(notes), onDelete = { deleted += it })

        compose.onNode(hasContentDescription("zametki", substring = true) and hasContentDescription("Forget", substring = true))
            .performClick()

        compose.onNodeWithText(str(R.string.settings_forget_alias_title)).assertIsDisplayed()
        assertEquals(emptyList<String>(), deleted)

        // the gate sits above the alias rows, so its confirmation is the first "Forget" button
        compose.onAllNodesWithText("Forget").onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf("zametki"), deleted)
    }

    /** Fix round, task 4: the two tabs are one width — that of the wider («Мои алиасы» / "My aliases"), not stretched. */
    @Test fun `the alias tabs are the same width`() {
        show(listOf(notes))

        val mine = compose.onNodeWithText(str(R.string.settings_aliases_tab_mine)).getUnclippedBoundsInRoot()
        val add = compose.onNodeWithText(str(R.string.settings_aliases_tab_add)).getUnclippedBoundsInRoot()
        assertEquals((mine.right - mine.left).value, (add.right - add.left).value, 1f)
    }
}
