package com.sidr.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.theme.AccentColor
import com.sidr.launcher.core.ui.theme.LocalSidrMotionEnabled
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    // Block X5, Fork X5-D: the composition root observes the persisted theme preference and maps it
    // to SidrTheme's darkTheme flag; only the light/dark scheme follows the user's system|light|dark
    // choice. AIL-0: dynamicColor defaults off (the cyberpunk-terminal brand palette replaces Material
    // You). AIL-6 / DF-7: the persisted brand accent (green | amber) is now mapped to SidrTheme's
    // AccentColor here — string → enum lives in :app so :domain stays free of the core/ui enum.
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    // DF-5 / DS-1: the block caret is LOW_END-gated. The device tier is read once (the profiler caches)
    // and provided down as LocalSidrMotionEnabled; LOW_END → static look. The global CRT scanline overlay
    // was removed by the 2026-07-10 visual-identity spec (identity is grey, not phosphor); `sidrScanlines`
    // stays defined for optional dev/boot use only.
    @Inject
    lateinit var deviceProfileProvider: DeviceProfileProvider

    private var homeResetSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by userPreferencesRepository.getPreferences()
                .collectAsState(initial = UserPreferences())
            val darkTheme = when (preferences.themeName) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val accent = when (preferences.accentColor) {
                "amber" -> AccentColor.AMBER
                else -> AccentColor.GREEN
            }
            val motionEnabled = remember { deviceProfileProvider.profile() != DeviceProfile.LOW_END }
            SidrTheme(darkTheme = darkTheme, accent = accent) {
                CompositionLocalProvider(LocalSidrMotionEnabled provides motionEnabled) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavHost(homeResetSignal = homeResetSignal)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeResetSignal += 1
    }
}
