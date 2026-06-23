package com.sidr.launcher.navigation

import android.util.Log
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
import com.sidr.launcher.feature.permission_education.PermissionEducationScreen

private const val TAG = "AppNavHost"

/**
 * Handles a [NavigationEvent] safely:
 * - [NavigationEvent.NavigateTo]: attempts to navigate to [event.route]; if the route is not
 *   registered in the graph (throws [IllegalArgumentException]), falls back to
 *   [Routes.Launcher.ROUTE] with popUpTo + launchSingleTop to avoid a growing back stack.
 * - [NavigationEvent.NavigateBack]: calls [NavHostController.popBackStack]; if it returns false
 *   (already at the root / no back stack entry), does nothing — avoids an infinite loop.
 *
 * Pass this helper to every [LaunchedEffect] that collects a NavigationEvent flow so the
 * fallback logic is not duplicated per destination.
 */
private fun handleNavigationEvent(
    navController: NavHostController,
    event: NavigationEvent,
) {
    when (event) {
        is NavigationEvent.NavigateTo -> {
            try {
                navController.navigate(event.route)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unknown route '${event.route}', falling back to launcher home", e)
                navController.navigate(Routes.Launcher.ROUTE) {
                    popUpTo(Routes.Launcher.ROUTE) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
        NavigationEvent.NavigateBack -> {
            val popped = navController.popBackStack()
            if (!popped) {
                Log.d(TAG, "popBackStack() returned false — already at root, ignoring NavigateBack")
            }
        }
    }
}

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
                    handleNavigationEvent(navController, event)
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

        // Real destination (Block G) — replaces the former inline placeholder. The screen owns
        // its own request flow; navigating back uses the same safe-fallback helper as every node.
        composable(Routes.PermissionEducation.ROUTE) {
            PermissionEducationScreen(
                onBack = {
                    handleNavigationEvent(navController, NavigationEvent.NavigateBack)
                },
            )
        }
    }
}
