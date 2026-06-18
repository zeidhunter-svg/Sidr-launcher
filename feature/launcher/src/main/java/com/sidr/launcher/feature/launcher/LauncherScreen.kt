package com.sidr.launcher.feature.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sidr.launcher.core.common.navigation.Routes

/**
 * Entry-point composable for the Launcher destination.
 * Full UI implemented in later steps.
 *
 * Navigation is triggered via [LauncherViewModel.navigateTo] — the ViewModel emits
 * a [com.sidr.launcher.core.common.navigation.NavigationEvent] that the app-level
 * NavHost collects and acts upon. No NavHostController reference is held here.
 */
@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = hiltViewModel(),
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(text = "Launcher Home")
        // Placeholder navigation trigger — proves NavigationEvent plumbing end-to-end.
        // Real UI and navigation triggers are added in later steps.
        Button(onClick = { viewModel.navigateTo(Routes.Assistant.ROUTE) }) {
            Text(text = "Open Assistant")
        }
    }
}
