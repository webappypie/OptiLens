package com.webappypie.optilens.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

/**
 * Root navigation host for OptiLens.
 *
 * Each screen composable is a stub in Phase 01 — replaced with real
 * implementations in Phase 02 (Design System & Navigation) and beyond.
 *
 * All routes use type-safe [AppDestination] with Navigation 2.10+.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Camera,
    // Screen composables injected so this host remains testable/previewable.
    cameraScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Camera") },
    galleryScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Gallery") },
    settingsScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Settings") },
    photoDetailScreen: @Composable (AppDestination.PhotoDetail) -> Unit = { NavigationPlaceholderScreen("Photo Detail") },
    proUpgradeScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Pro Upgrade") },
    onboardingScreen: @Composable () -> Unit = { NavigationPlaceholderScreen("Onboarding") },
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<AppDestination.Camera>      { cameraScreen() }
        composable<AppDestination.Gallery>     { galleryScreen() }
        composable<AppDestination.Settings>    { settingsScreen() }
        composable<AppDestination.PhotoDetail> { backStackEntry ->
            photoDetailScreen(backStackEntry.toRoute())
        }
        composable<AppDestination.ProUpgrade>  { proUpgradeScreen() }
        composable<AppDestination.Onboarding>  { onboardingScreen() }
    }
}
