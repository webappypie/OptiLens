package com.webappypie.optilens.ui.theme

import androidx.compose.runtime.Composable
import com.webappypie.optilens.core.settings.ThemeMode
import com.webappypie.optilens.core.ui.theme.OptiLensTheme as CoreOptiLensTheme

/**
 * App-level entry point delegating to :core:ui design system.
 */
@Composable
fun OptiLensTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    CoreOptiLensTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        content = content,
    )
}
