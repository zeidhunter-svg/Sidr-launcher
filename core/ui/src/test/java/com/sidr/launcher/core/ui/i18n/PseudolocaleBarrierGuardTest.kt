package com.sidr.launcher.core.ui.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.ui.R
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * I18N-1 barrier 3, self-check (spec §10.4).
 *
 * The pseudolocale screenshot barrier (`assistant_pseudolocale`, `prayer_summary_pseudolocale`)
 * depends on `isPseudoLocalesEnabled = true` in `core/ui/build.gradle.kts`. That dependency fails
 * **silently**: with the flag gone, a `b+en+XA` qualifier resolves to plain English, the captures
 * re-record as English, and every screenshot test stays green while proving nothing.
 *
 * This test is the alarm. It asserts the transform is actually happening, so dropping the flag turns
 * one obvious red test instead of two quietly meaningless goldens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "+b+en+XA")
class PseudolocaleBarrierGuardTest {

    @Test
    fun pseudolocale_actually_transforms_strings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = "Cancel"

        assertNotEquals(
            "Pseudolocale expansion is NOT active: ui_action_cancel resolved to the base English " +
                "\"$base\" under the b+en+XA qualifier. Most likely cause: the " +
                "`buildTypes { debug { isPseudoLocalesEnabled = true } }` block was removed from " +
                "core/ui/build.gradle.kts. Without it the pseudolocale goldens " +
                "(assistant_pseudolocale, prayer_summary_pseudolocale) silently degrade to plain " +
                "English and stop being a layout-expansion barrier.",
            base,
            context.getString(R.string.ui_action_cancel),
        )
    }
}
