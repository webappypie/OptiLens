package com.webappypie.optilens.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.webappypie.optilens.core.settings.ThemeMode

/**
 * OptiLens Master Design System Theme.
 *
 * Supports:
 * - System, Light, and Dark modes backed by [ThemeMode].
 * - Comprehensive design tokens: spacing, shapes, elevation, icon sizes, and camera overlay neutrals.
 * - Dynamic color (API 31+) when enabled, otherwise branded photographic palettes.
 */
@Composable
fun OptiLensTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> OptiLensDarkColorScheme
        else   -> OptiLensLightColorScheme
    }

    val spacing = OptiLensSpacing()
    val elevation = OptiLensElevation()
    val iconSizes = OptiLensIconSizes()
    val overlayColors = CameraOverlayColors()

    CompositionLocalProvider(
        LocalOptiLensSpacing provides spacing,
        LocalOptiLensElevation provides elevation,
        LocalOptiLensIconSizes provides iconSizes,
        LocalCameraOverlayColors provides overlayColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = OptiLensTypography,
            shapes      = OptiLensShapes,
            content     = content,
        )
    }
}

/**
 * Convenient object accessor for design tokens within composables.
 */
object OptiLensTheme {
    val spacing: OptiLensSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalOptiLensSpacing.current

    val elevation: OptiLensElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalOptiLensElevation.current

    val iconSizes: OptiLensIconSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalOptiLensIconSizes.current

    val overlayColors: CameraOverlayColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCameraOverlayColors.current
}
