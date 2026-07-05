package com.sidr.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    // Block X5, Fork X5-D: the composition root observes the persisted theme preference and maps it
    // to SidrTheme's darkTheme flag; only the light/dark scheme follows the user's system|light|dark
    // choice. AIL-0: dynamicColor now defaults off (the cyberpunk-terminal brand palette replaces
    // Material You); the brand accent defaults to green. A user accent switcher is a later step (DF-7).
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

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
            SidrTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(homeResetSignal = homeResetSignal)
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
