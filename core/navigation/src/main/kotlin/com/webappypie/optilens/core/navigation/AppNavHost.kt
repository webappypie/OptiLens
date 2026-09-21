package com.webappypie.optilens.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute

/**
 * Root navigation host for OptiLens.
 *
 * All routes use type-safe [AppDestination] with Navigation 2.10+.
 * Includes campaign deep links (optilens://) and universal App Links (https://optilens.app/).
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Camera,
    cameraScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Camera") },
    galleryScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Gallery") },
    settingsScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Settings") },
    aiToolsScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("AI Tools") },
    photoDetailScreen: @Composable (AppDestination.PhotoDetail) -> Unit = { NavigationPlaceholderScreen("Photo Detail") },
    proUpgradeScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Pro Upgrade") },
    onboardingScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Onboarding") },
    cameraDiagnosticsScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Camera Diagnostics") },
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<AppDestination.Camera>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "optilens://camera" },
                navDeepLink { uriPattern = "https://optilens.app/camera" },
            ),
        ) { cameraScreen() }

        composable<AppDestination.Gallery>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "optilens://gallery" },
                navDeepLink { uriPattern = "https://optilens.app/gallery" },
            ),
        ) { galleryScreen() }

        composable<AppDestination.Settings>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "optilens://settings" },
                navDeepLink { uriPattern = "https://optilens.app/settings" },
            ),
        ) { settingsScreen() }

        composable<AppDestination.AiTools>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "optilens://enhance" },
                navDeepLink { uriPattern = "optilens://aitools" },
                navDeepLink { uriPattern = "https://optilens.app/enhance" },
                navDeepLink { uriPattern = "https://optilens.app/aitools" },
            ),
        ) { aiToolsScreen() }

        composable<AppDestination.PhotoDetail> { backStackEntry ->
            photoDetailScreen(backStackEntry.toRoute())
        }

        composable<AppDestination.ProUpgrade>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "optilens://upgrade" },
                navDeepLink { uriPattern = "optilens://pro" },
                navDeepLink { uriPattern = "https://optilens.app/upgrade" },
                navDeepLink { uriPattern = "https://optilens.app/pro" },
            ),
        ) { proUpgradeScreen() }

        composable<AppDestination.Onboarding> { onboardingScreen() }

        composable<AppDestination.CameraDiagnostics>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "optilens://diagnostics" },
                navDeepLink { uriPattern = "https://optilens.app/diagnostics" },
            ),
        ) { cameraDiagnosticsScreen() }
    }
}
