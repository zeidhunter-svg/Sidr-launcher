package com.sidr.launcher.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sidr.launcher.core.common.navigation.Routes

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
            // Placeholder: full launcher home UI wired in step 3.1.3
            Text(text = "Launcher Home")
        }

        composable(Routes.Assistant.ROUTE) {
            // Placeholder: full assistant UI wired in step 3.1.3
            Text(text = "Assistant")
        }

        composable(Routes.Settings.ROUTE) {
            // Placeholder: full settings UI wired in step 3.1.3
            Text(text = "Settings")
        }

        composable(Routes.PermissionEducation.ROUTE) {
            // Placeholder: full permission education UI wired in step 3.1.3
            Text(text = "Permission Education")
        }
    }
}
