package com.sidr.launcher.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
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
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.feature.assistant.AssistantProviderScreen
import com.sidr.launcher.feature.assistant.AssistantScreen
import com.sidr.launcher.feature.assistant.AssistantViewModel
import com.sidr.launcher.feature.launcher.AppDrawerScreen
import com.sidr.launcher.feature.launcher.AppDrawerViewModel
import com.sidr.launcher.feature.launcher.LauncherScreen
import com.sidr.launcher.feature.launcher.LauncherViewModel
import com.sidr.launcher.feature.permission_education.PermissionEducationScreen
import com.sidr.launcher.feature.settings.LearnedChoicesScreen
import com.sidr.launcher.feature.settings.SettingsScreen
import com.sidr.launcher.feature.settings.SettingsViewModel
import com.sidr.launcher.feature.suggestions.SuggestionsRow

private const val TAG = "AppNavHost"

/** Maps a [SidrTab] to its root [Routes] destination (Task 7). */
private fun routeForTab(tab: SidrTab): String = when (tab) {
    SidrTab.HOME -> Routes.Launcher.ROUTE
    SidrTab.TASKS -> Routes.Tasks.ROUTE
    SidrTab.AGENTS -> Routes.Agents.ROUTE
    SidrTab.ACTIVITY -> Routes.Activity.ROUTE
    SidrTab.TERMINAL -> Routes.Terminal.ROUTE
}

/**
 * Switches the visible tab root. `popUpTo(Routes.Launcher.ROUTE)` keeps tab switches from
 * growing the back stack (Home is the permanent base of the tab back-stack), and
 * `launchSingleTop` avoids stacking duplicate copies of the same tab.
 */
private fun navigateToTab(navController: NavHostController, tab: SidrTab) {
    navController.navigate(routeForTab(tab)) {
        popUpTo(Routes.Launcher.ROUTE) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Wraps a single tab-root destination's content in a [Scaffold] with the shared [SidrTabBar] as
 * its bottom bar. Only the five tab roots (Home + the four preview tabs) use this — pushed
 * destinations (App Drawer, Settings, Assistant, provider setup, learned choices, permission
 * education) render unwrapped so the tab bar naturally disappears on push and reappears on pop.
 *
 * [Modifier.consumeWindowInsets] marks [inner] as already handled for the subtree below: since
 * [LauncherScreen] (and the preview stubs) wrap their own content in a `SidrScaffold` internally,
 * without this the nested Scaffold would independently re-measure the same system-bar insets
 * (e.g. the status bar) that this outer Scaffold already accounted for, double-padding the top of
 * the screen. Consuming here keeps that inner Scaffold's own inset calculation correct.
 */
@Composable
private fun TabRootScaffold(
    tab: SidrTab,
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = { SidrTabBar(selected = tab, onSelect = { navigateToTab(navController, it) }) },
    ) { inner ->
        Box(modifier = Modifier.consumeWindowInsets(inner)) {
            content(inner)
        }
    }
}

private fun navigateHome(navController: NavHostController) {
    navController.navigate(Routes.Launcher.ROUTE) {
        popUpTo(Routes.Launcher.ROUTE) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Handles a [NavigationEvent] safely:
 * - [NavigationEvent.NavigateTo]: attempts to navigate to [event.route]; if the route is not
 *   registered in the graph (throws [IllegalArgumentException]), falls back to
 *   [Routes.Launcher.ROUTE] with popUpTo + launchSingleTop to avoid a growing back stack.
 *   Fallback logs stay payload-free: routes can carry user prompt text in query args.
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
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Navigation route was not registered; falling back to launcher home.")
                navigateHome(navController)
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
    homeResetSignal: Int = 0,
) {
    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal > 0) {
            navigateHome(navController)
        }
    }

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
            TabRootScaffold(SidrTab.HOME, navController) { inner ->
                LauncherScreen(
                    viewModel = viewModel,
                    suggestionsContent = { suggestions, onSuggestionTap ->
                        SuggestionsRow(
                            suggestions = suggestions,
                            onSuggestionTap = onSuggestionTap,
                        )
                    },
                    modifier = Modifier.padding(inner),
                )
            }
        }

        // Vision MVP preview tab roots (Task 7): four additive, non-functional tab destinations.
        // Each renders only a centred SidrPreviewBanner inline for now — Tasks 8–11 replace these
        // bodies with real TasksPreviewScreen/AgentsPreviewScreen/ActivityPreviewScreen/
        // TerminalPreviewScreen composables (deliberately NOT declared here to avoid colliding with
        // those future definitions).
        composable(Routes.Tasks.ROUTE) {
            TabRootScaffold(SidrTab.TASKS, navController) { inner ->
                Box(
                    modifier = Modifier.padding(inner).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SidrPreviewBanner()
                }
            }
        }

        composable(Routes.Agents.ROUTE) {
            TabRootScaffold(SidrTab.AGENTS, navController) { inner ->
                Box(
                    modifier = Modifier.padding(inner).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SidrPreviewBanner()
                }
            }
        }

        composable(Routes.Activity.ROUTE) {
            TabRootScaffold(SidrTab.ACTIVITY, navController) { inner ->
                Box(
                    modifier = Modifier.padding(inner).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SidrPreviewBanner()
                }
            }
        }

        composable(Routes.Terminal.ROUTE) {
            TabRootScaffold(SidrTab.TERMINAL, navController) { inner ->
                Box(
                    modifier = Modifier.padding(inner).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SidrPreviewBanner()
                }
            }
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

        // Dedicated AI-provider setup surface (base URL / model / API key), reached from Settings.
        // Kept separate from the Assistant chat so the key-bearing form never overlaps it.
        composable(Routes.AssistantProvider.ROUTE) {
            val vm: AssistantViewModel = hiltViewModel()
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            AssistantProviderScreen(viewModel = vm)
        }

        composable(Routes.LearnedChoices.ROUTE) {
            LearnedChoicesScreen(
                onBack = {
                    handleNavigationEvent(navController, NavigationEvent.NavigateBack)
                },
            )
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
