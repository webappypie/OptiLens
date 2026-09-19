package com.webappypie.optilens.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary                 = OptiLensPrimary,
    onPrimary               = OptiLensOnPrimary,
    primaryContainer        = OptiLensPrimaryContainer,
    onPrimaryContainer      = OptiLensOnPrimaryContainer,
    secondary               = OptiLensSecondary,
    onSecondary             = OptiLensOnSecondary,
    secondaryContainer      = OptiLensSecondaryContainer,
    onSecondaryContainer    = OptiLensOnSecondaryContainer,
    background              = OptiLensBackground,
    onBackground            = OptiLensOnBackground,
    surface                 = OptiLensSurface,
    onSurface               = OptiLensOnSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary                 = OptiLensPrimaryDark,
    onPrimary               = OptiLensOnPrimaryDark,
    primaryContainer        = OptiLensPrimaryContainerDark,
    onPrimaryContainer      = OptiLensOnPrimaryContainerDark,
    secondary               = OptiLensSecondaryDark,
    onSecondary             = OptiLensOnSecondaryDark,
    secondaryContainer      = OptiLensSecondaryContainerDark,
    onSecondaryContainer    = OptiLensOnSecondaryContainerDark,
    background              = OptiLensBackgroundDark,
    onBackground            = OptiLensOnBackgroundDark,
    surface                 = OptiLensSurfaceDark,
    onSurface               = OptiLensOnSurfaceDark,
)

/**
 * OptiLens Material 3 theme.
 *
 * Dynamic color (Android 12+) is supported but falls back to the branded
 * palette on older API levels.
 *
 * Light / Dark / System preference will be user-controllable from Settings
 * once Phase 02 (Design System) is complete.
 */
@Composable
fun OptiLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color: supported on Android 12+ (API 31+)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = OptiLensTypography,
        content     = content,
    )
}
