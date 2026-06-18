package com.sidr.launcher.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.feature.assistant.AssistantScreen
import com.sidr.launcher.feature.launcher.LauncherScreen
import com.sidr.launcher.feature.launcher.LauncherViewModel

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Launcher.ROUTE,
        modifier = modifier,
    ) {
        composable(Routes.Launcher.ROUTE) {
            val viewModel: LauncherViewModel = hiltViewModel()
            LaunchedEffect(viewModel.navigationEvents) {
                viewModel.navigationEvents.collect { event ->
                    when (event) {
                        is NavigationEvent.NavigateTo -> navController.navigate(event.route)
                        NavigationEvent.NavigateBack -> navController.popBackStack()
                    }
                }
            }
            LauncherScreen(viewModel = viewModel)
        }

        composable(Routes.Assistant.ROUTE) {
            AssistantScreen()
        }

        // No feature:settings module yet — inline placeholder until module is created
        composable(Routes.Settings.ROUTE) {
            Text(text = "Settings")
        }

        // No feature:permission_education module yet — inline placeholder until module is created
        composable(Routes.PermissionEducation.ROUTE) {
            Text(text = "Permission Education")
        }
    }
}
