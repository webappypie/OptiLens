package com.webappypie.optilens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.navigation.AppDestination
import com.webappypie.optilens.core.navigation.AppNavHost
import com.webappypie.optilens.core.navigation.NavigationCommand
import com.webappypie.optilens.core.navigation.NavigationManager
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.settings.ThemeMode
import com.webappypie.optilens.core.ui.camera.CameraScreen
import com.webappypie.optilens.core.ui.screens.AiToolsScreen
import com.webappypie.optilens.core.ui.screens.GalleryScreen
import com.webappypie.optilens.core.ui.screens.ProUpgradeScreen
import com.webappypie.optilens.core.ui.screens.SettingsScreen
import com.webappypie.optilens.ui.theme.OptiLensTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var logger: AppLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger.d(TAG, "MainActivity created")

        setContent {
            val themeMode by appSettings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            OptiLensTheme(themeMode = themeMode) {
                OptiLensNavigationShell(
                    navigationManager = navigationManager,
                    appSettings = appSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun OptiLensNavigationShell(
    navigationManager: NavigationManager,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    LaunchedEffect(navController, navigationManager) {
        navigationManager.navigationCommands.collect { command ->
            when (command) {
                is NavigationCommand.NavigateTo -> {
                    navController.navigate(command.destination) {
                        command.popUpTo?.let { popDest ->
                            popUpTo(popDest) {
                                inclusive = command.inclusive
                            }
                        }
                        launchSingleTop = command.singleTop
                    }
                }
                is NavigationCommand.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    val gridEnabled by appSettings.gridEnabled.collectAsStateWithLifecycle(initialValue = true)
    val levelEnabled by appSettings.levelEnabled.collectAsStateWithLifecycle(initialValue = true)

    AppNavHost(
        modifier = modifier,
        navController = navController,
        cameraScreen = {
            CameraScreen(
                onNavigateToSettings = { navController.navigate(AppDestination.Settings) },
                onNavigateToGallery  = { navController.navigate(AppDestination.Gallery) },
                showGrid = gridEnabled,
                showLevel = levelEnabled,
            )
        },
        galleryScreen = {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = { navController.popBackStack() },
            )
        },
        settingsScreen = {
            SettingsScreen(
                appSettings = appSettings,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPro = { navController.navigate(AppDestination.ProUpgrade) },
            )
        },
        aiToolsScreen = {
            AiToolsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        },
        proUpgradeScreen = {
            ProUpgradeScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        },
    )
}
