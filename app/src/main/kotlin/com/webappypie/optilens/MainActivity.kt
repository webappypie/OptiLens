package com.webappypie.optilens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.navigation.AppNavHost
import com.webappypie.optilens.core.navigation.NavigationCommand
import com.webappypie.optilens.core.navigation.NavigationManager
import com.webappypie.optilens.ui.theme.OptiLensTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var logger: AppLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger.d(TAG, "MainActivity created")

        setContent {
            OptiLensTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OptiLensNavigationShell(
                        navigationManager = navigationManager,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
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

    AppNavHost(
        modifier = modifier,
        navController = navController,
    )
}
