package com.sidr.launcher.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupHomeReady() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait(launcherIntent())
            device.wait(
                Until.hasObject(By.textContains("Search or type a command")),
                STARTUP_TIMEOUT_MS,
            )
        }
    }

    private fun launcherIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .setComponent(ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.LauncherActivity"))
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val PACKAGE_NAME = "com.sidr.launcher"
        const val STARTUP_TIMEOUT_MS = 5_000L
    }
}
