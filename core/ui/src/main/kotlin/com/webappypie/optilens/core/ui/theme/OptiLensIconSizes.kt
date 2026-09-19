package com.webappypie.optilens.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class OptiLensIconSizes(
    /** Small icons for tags, badges, and inline status indicators. */
    val small: Dp = 16.dp,

    /** Medium icons for chips, compact buttons, and secondary controls. */
    val medium: Dp = 20.dp,

    /** Standard icon size for top bars, navigation, and list rows. */
    val standard: Dp = 24.dp,

    /** Large icon size for viewfinder quick-actions and hero icons. */
    val large: Dp = 32.dp,

    /** Extra large icons for empty states, permission illustrations. */
    val extraLarge: Dp = 48.dp,

    /** Mandatory minimum touch target according to Android Accessibility guidelines. */
    val minTouchTarget: Dp = 48.dp,
)

val LocalOptiLensIconSizes = staticCompositionLocalOf { OptiLensIconSizes() }
