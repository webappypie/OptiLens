package com.webappypie.optilens

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.navigation.AppDestination
import com.webappypie.optilens.core.navigation.AppNavHost
import com.webappypie.optilens.core.navigation.NavigationCommand
import com.webappypie.optilens.core.navigation.NavigationManager
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.settings.ThemeMode
import com.webappypie.optilens.core.ui.camera.CameraScreen
import com.webappypie.optilens.core.ui.review.PhotoReviewScreen
import com.webappypie.optilens.core.ui.screens.AiToolsScreen
import com.webappypie.optilens.core.ui.screens.CameraDiagnosticsScreen
import com.webappypie.optilens.core.ui.screens.GalleryScreen
import com.webappypie.optilens.core.ui.screens.ProUpgradeScreen
import com.webappypie.optilens.core.ui.screens.SettingsScreen
import com.webappypie.optilens.ui.theme.OptiLensTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var logger: AppLogger

    private var isVolumeKeyShutterEnabled = false
    private val _volumeKeyShutterTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val volumeKeyShutterTrigger: Flow<Unit> = _volumeKeyShutterTrigger.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger.d(TAG, "MainActivity created")

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appSettings.volumeKeyShutterEnabled.collect { enabled ->
                    isVolumeKeyShutterEnabled = enabled
                }
            }
        }

        setContent {
            val themeMode by appSettings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            OptiLensTheme(themeMode = themeMode) {
                OptiLensNavigationShell(
                    navigationManager = navigationManager,
                    appSettings = appSettings,
                    volumeKeyShutterTrigger = volumeKeyShutterTrigger,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKeyShutterEnabled &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            _volumeKeyShutterTrigger.tryEmit(Unit)
            return true // Consume key event to avoid unwanted media volume changes while shooting
        }
        return super.onKeyDown(keyCode, event)
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
    volumeKeyShutterTrigger: Flow<Unit>? = null,
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
                onNavigateToPhotoReview = { uri -> navController.navigate(AppDestination.PhotoDetail(uri)) },
                showGrid = gridEnabled,
                showLevel = levelEnabled,
                externalShutterTrigger = volumeKeyShutterTrigger,
            )
        },
        photoDetailScreen = { detail ->
            PhotoReviewScreen(
                photoUri = detail.photoUri,
                onNavigateBack = { navController.popBackStack() },
            )
        },
        galleryScreen = {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = { navController.popBackStack() },
                onNavigateToPhotoDetail = { uri -> navController.navigate(AppDestination.PhotoDetail(uri)) },
            )
        },
        settingsScreen = {
            SettingsScreen(
                appSettings = appSettings,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPro = { navController.navigate(AppDestination.ProUpgrade) },
                onNavigateToDiagnostics = { navController.navigate(AppDestination.CameraDiagnostics) },
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
        cameraDiagnosticsScreen = {
            CameraDiagnosticsScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        },
    )
}
