package com.sidr.launcher.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.sidr.launcher.feature.assistant.AssistantProviderScreen
import com.sidr.launcher.feature.assistant.AssistantScreen
import com.sidr.launcher.feature.assistant.AssistantViewModel
import com.sidr.launcher.feature.launcher.AppDrawerScreen
import com.sidr.launcher.feature.launcher.AppDrawerViewModel
import com.sidr.launcher.feature.launcher.LauncherScreen
import com.sidr.launcher.feature.launcher.LauncherViewModel
import com.sidr.launcher.feature.launcher.preview.ActivityPreviewScreen
import com.sidr.launcher.feature.launcher.preview.AgentsPreviewScreen
import com.sidr.launcher.feature.launcher.preview.MomentsPreviewScreen
import com.sidr.launcher.feature.launcher.preview.TasksPreviewScreen
import com.sidr.launcher.feature.launcher.preview.TerminalPreviewScreen
import com.sidr.launcher.feature.permission_education.PermissionEducationScreen
import com.sidr.launcher.feature.prayer.PrayerDetailScreen
import com.sidr.launcher.feature.prayer.PrayerDetailViewModel
import com.sidr.launcher.feature.prayer.PrayerSettingsScreen
import com.sidr.launcher.feature.prayer.PrayerSettingsViewModel
import com.sidr.launcher.feature.settings.AliasesScreen
import com.sidr.launcher.feature.settings.LearnedChoicesScreen
import com.sidr.launcher.feature.settings.SettingsScreen
import com.sidr.launcher.feature.settings.SettingsViewModel
import com.sidr.launcher.feature.suggestions.SuggestionsRow
import kotlinx.coroutines.delay

private const val TAG = "AppNavHost"

/** Idle delay before the bottom nav chrome auto-hides to its handle on a tab root (2026-07-12). */
private const val NAV_AUTO_HIDE_MILLIS = 5_000L

/** Maps a [SidrTab] to its root [Routes] destination (Task 7; Apps added 2026-07-12). */
private fun routeForTab(tab: SidrTab): String = when (tab) {
    SidrTab.HOME -> Routes.Launcher.ROUTE
    SidrTab.APPS -> Routes.AppDrawer.ROUTE
    SidrTab.TASKS -> Routes.Tasks.ROUTE
    SidrTab.AGENTS -> Routes.Agents.ROUTE
    SidrTab.ACTIVITY -> Routes.Activity.ROUTE
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
 * Wraps a single tab-root destination's content in a [Scaffold] whose bottom chrome is
 * [SidrTabBar] (the true bottom-most strip on every tab) plus, **Home only**, the shared
 * [SidrAppFooter] (privacy note / Terminal icon / Settings gear / `SIDR OS` wordmark) stacked
 * above it (order fixed 2026-07-12 per owner direction). The footer was found (2026-07-12) to be
 * appearing on every tab root when it was only ever meant to be Home's; it is now gated on
 * `tab == SidrTab.HOME` so Apps/Tasks/Agents/Activity show only the tab bar. Pushed destinations
 * (Settings, Assistant, provider setup, learned choices, permission education, interaction
 * moments, Terminal) render unwrapped so this whole bottom chrome naturally disappears on push and
 * reappears on pop.
 *
 * [onArmDevMode] is the hidden dev-console arm on the footer's `SIDR OS` wordmark — a Home-only
 * concept (it drives `LauncherViewModel`'s console overlay); since the footer itself is now
 * Home-only, only the Home call site ever supplies a real callback.
 *
 * [Modifier.consumeWindowInsets] marks [inner] as already handled for the subtree below: since
 * [LauncherScreen] (and the preview stubs) wrap their own content in a `SidrScaffold` internally,
 * without this the nested Scaffold would independently re-measure the same system-bar insets
 * (e.g. the status bar) that this outer Scaffold already accounted for, double-padding the top of
 * the screen. Consuming here keeps that inner Scaffold's own inset calculation correct.
 *
 * `contentWindowInsets = WindowInsets.systemBars` (2026-07-12, part of the keyboard-bug fix): M3
 * [Scaffold]'s own default is `WindowInsets.safeDrawing`, which — unlike the name suggests —
 * includes the **IME** inset, not just status/navigation bars; restricting this outer chrome
 * Scaffold to `systemBars` stops it reacting to the keyboard at the Compose-insets level. The
 * other half of that fix is in [com.sidr.launcher.LauncherActivity]: without
 * `WindowCompat.setDecorFitsSystemWindows(window, false)` there, `windowSoftInputMode="adjustResize"`
 * (manifest) makes the OS physically shrink the whole Activity window when the IME opens on API<35
 * devices, which this Scaffold's own `contentWindowInsets` alone cannot undo — the window itself has
 * to stop resizing. The screens that actually need to dodge the keyboard apply their own
 * `Modifier.imePadding()` at the right level ([LauncherScreen], [com.sidr.launcher.feature.launcher.preview.TerminalPreviewScreen]).
 */
@Composable
private fun TabRootScaffold(
    tab: SidrTab,
    navController: NavHostController,
    autoHideNav: Boolean = false,
    onArmDevMode: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Calm/idle-hide bottom nav (2026-07-12): the chrome is visible when a tab root appears and
    // auto-hides to a thin [SidrChromeHandle] after [NAV_AUTO_HIDE_MILLIS] of no navigation, leaving a
    // quieter, roomier screen. Tapping the handle summons it back and restarts the idle timer. Active
    // tab-hopping never has to summon it: tapping a tab re-navigates, and because Navigation-Compose
    // disposes a non-current tab root, the destination re-enters composition with `chromeVisible = true`
    // — hence plain `remember` (NOT `rememberSaveable`): each arrival re-initialises to visible for free.
    // Unless the user opts into auto-hide in Settings ([autoHideNav]) the chrome stays pinned and the
    // handle/timer are inert.
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(autoHideNav, chromeVisible) {
        if (autoHideNav && chromeVisible) {
            delay(NAV_AUTO_HIDE_MILLIS)
            chromeVisible = false
        }
    }
    val showChrome = !autoHideNav || chromeVisible

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            // The full chrome stays in Scaffold's bottomBar. The hidden reveal handle is an overlay
            // below, so it can apply navigationBarsPadding in the edge-to-edge window.
            if (showChrome) {
                Column {
                    if (tab == SidrTab.HOME) {
                        SidrAppFooter(
                            onSettings = { navController.navigate(Routes.Settings.ROUTE) },
                            onTerminal = { navController.navigate(Routes.Terminal.ROUTE) },
                            onArmDevMode = onArmDevMode,
                        )
                    }
                    SidrTabBar(selected = tab, onSelect = { navigateToTab(navController, it) })
                }
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.consumeWindowInsets(inner)) {
                content(inner)
            }
            if (!showChrome) {
                SidrChromeHandle(
                    onReveal = { chromeVisible = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
            }
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
    // When true, the bottom nav chrome is pinned permanently visible instead of auto-hiding after idle
    // (2026-07-12; sourced from UserPreferences.autoHideNavBar via LauncherActivity).
    autoHideNav: Boolean = false,
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
            TabRootScaffold(SidrTab.HOME, navController, autoHideNav = autoHideNav, onArmDevMode = viewModel::armDevMode) { inner ->
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

        // "Apps" tab (2026-07-12): the App Drawer, promoted from a Home-body row into a real tab
        // root — a peer of Home, not a pushed destination, so (like every other tab root) it has no
        // back arrow of its own (onBack stays null; the tab bar is how you leave). Own ViewModel +
        // navigation channel for anything besides "back" (e.g. the "Ask assistant" affordance).
        composable(Routes.AppDrawer.ROUTE) {
            val vm: AppDrawerViewModel = hiltViewModel()
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            TabRootScaffold(SidrTab.APPS, navController, autoHideNav = autoHideNav) { inner ->
                AppDrawerScreen(viewModel = vm, modifier = Modifier.padding(inner))
            }
        }

        // Vision MVP preview tab roots (Task 7): additive, non-functional tab destinations.
        // Tasks/Agents/Activity are real, non-functional preview screens (Tasks 8/9/10) — no stub
        // bodies remain. Terminal (Task 11) is no longer a tab root — see the registration below,
        // reached via the icon-only button in [SidrAppFooter] instead (2026-07-12).
        composable(Routes.Tasks.ROUTE) {
            TabRootScaffold(SidrTab.TASKS, navController, autoHideNav = autoHideNav) { inner ->
                TasksPreviewScreen(
                    modifier = Modifier.padding(inner),
                    onOpenMoments = { navController.navigate(Routes.Moments.ROUTE) },
                )
            }
        }

        composable(Routes.Agents.ROUTE) {
            TabRootScaffold(SidrTab.AGENTS, navController, autoHideNav = autoHideNav) { inner ->
                AgentsPreviewScreen(modifier = Modifier.padding(inner))
            }
        }

        composable(Routes.Activity.ROUTE) {
            TabRootScaffold(SidrTab.ACTIVITY, navController, autoHideNav = autoHideNav) { inner ->
                ActivityPreviewScreen(modifier = Modifier.padding(inner))
            }
        }

        // Terminal preview (Task 11; demoted from a tab root to a pushed destination 2026-07-12):
        // reached via the icon-only button in every tab's [SidrAppFooter], not a tab of its own —
        // so, like every other pushed destination, it renders unwrapped (no tab bar/footer) with its
        // own real back arrow.
        composable(Routes.Terminal.ROUTE) {
            TerminalPreviewScreen(
                onBack = { handleNavigationEvent(navController, NavigationEvent.NavigateBack) },
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

        composable(Routes.Aliases.ROUTE) {
            AliasesScreen(
                onBack = {
                    handleNavigationEvent(navController, NavigationEvent.NavigateBack)
                },
            )
        }

        // Prayer setup + detail (DS-6B Task 8): two pushed, unwrapped destinations (bottom tab bar
        // hides on push, like every other node in this section) reached from Settings ("Prayer
        // times" row) and from the (Task 9) Home strip respectively.
        // The optional `section` arg scopes setup to one setting (Method/Madhab/Location) when the
        // detail screen deep-links into it; the bare `prayer_settings` route still matches and still
        // opens the whole page, which is what Settings' "Prayer times" row and first run use.
        composable(
            route = Routes.PrayerSettings.ROUTE_WITH_ARG,
            arguments = listOf(
                navArgument(Routes.PrayerSettings.ARG_SECTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val vm: PrayerSettingsViewModel = hiltViewModel()
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            PrayerSettingsScreen(viewModel = vm)
        }

        composable(Routes.PrayerDetail.ROUTE) {
            val vm: PrayerDetailViewModel = hiltViewModel()
            LaunchedEffect(vm.navigationEvents) {
                vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
            }
            PrayerDetailScreen(viewModel = vm)
        }

        // Interaction-moment previews (Task 12): a pushed, non-tab destination reachable from the
        // Tasks preview footer. Non-functional sample-data screen; only `onBack` is real.
        composable(Routes.Moments.ROUTE) {
            MomentsPreviewScreen(
                onBack = { handleNavigationEvent(navController, NavigationEvent.NavigateBack) },
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
