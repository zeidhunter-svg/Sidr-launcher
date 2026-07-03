package com.sidr.launcher.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.feature.assistant.AssistantScreen
import com.sidr.launcher.feature.assistant.AssistantViewModel
import com.sidr.launcher.feature.launcher.AppDrawerScreen
import com.sidr.launcher.feature.launcher.AppDrawerViewModel
import com.sidr.launcher.feature.launcher.LauncherScreen
import com.sidr.launcher.feature.launcher.LauncherViewModel
import com.sidr.launcher.feature.permission_education.PermissionEducationScreen
import com.sidr.launcher.feature.settings.SettingsScreen
import com.sidr.launcher.feature.settings.SettingsViewModel
import com.sidr.launcher.feature.suggestions.SuggestionsRow

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
            LauncherScreen(
                viewModel = viewModel,
                suggestionsContent = { suggestions, onSuggestionTap ->
                    SuggestionsRow(
                        suggestions = suggestions,
                        onSuggestionTap = onSuggestionTap,
                    )
                },
            )
        }

        // Assistant (Block N; Block X6-C adds the optional prompt prefill). The `prompt` arg is
        // optional (bare `assistant` still matches), read here from the back-stack entry and passed
        // to the screen as a one-shot initial value — it never enters the VM's (absent) SavedStateHandle.
        composable(
            route = Routes.Assistant.ROUTE_WITH_ARG,
            arguments = listOf(
                navArgument(Routes.Assistant.ARG_PROMPT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val vm: AssistantViewModel = hiltViewModel()
            val initialPrompt = backStackEntry.arguments?.getString(Routes.Assistant.ARG_PROMPT)
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            AssistantScreen(viewModel = vm, initialPrompt = initialPrompt)
        }

        // App Drawer (Block X3): the full installed-apps list, opened from the home "All apps"
        // affordance. Own ViewModel + navigation channel; Back returns to home via the shared
        // safe-fallback helper. Registering it retires the interim safe-fallback-to-home behaviour.
        composable(Routes.AppDrawer.ROUTE) {
            val vm: AppDrawerViewModel = hiltViewModel()
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            AppDrawerScreen(
                viewModel = vm,
                onBack = {
                    handleNavigationEvent(navController, NavigationEvent.NavigateBack)
                },
            )
        }

        composable(Routes.Settings.ROUTE) {
            val vm: SettingsViewModel = hiltViewModel()
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            SettingsScreen(viewModel = vm)
        }

        // Real destination (Block G; Block T routes the feature in). The optional `feature` arg
        // selects which PermissionFeature to educate (defaults to WALLPAPER when absent — so a bare
        // `permission_education` route still matches). The screen owns its own request flow;
        // navigating back uses the same safe-fallback helper as every node.
        composable(
            route = Routes.PermissionEducation.ROUTE_WITH_ARG,
            arguments = listOf(
                navArgument(Routes.PermissionEducation.ARG_FEATURE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            PermissionEducationScreen(
                onBack = {
                    handleNavigationEvent(navController, NavigationEvent.NavigateBack)
                },
            )
        }
    }
}
