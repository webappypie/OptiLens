package com.webappypie.optilens.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * High-contrast neutrals and accents specifically calibrated for
 * the camera viewfinder overlay controls.
 *
 * Viewfinder overlays must remain distinctly readable against any scene
 * (from direct sunlight/bright sky to pitch black night).
 */
@Immutable
data class CameraOverlayColors(
    /** Semi-transparent black scrim for top and bottom chrome. */
    val scrimBackground: Color = Color(0x8A000000),

    /** Backdrop for floating circular/pill buttons on the viewfinder. */
    val controlSurface: Color = Color(0x66181B22),

    /** Backdrop when a control is selected or active (e.g. Flash ON). */
    val controlSurfaceActive: Color = Color(0xFFFFB74D),

    /** Border outline for viewfinder controls. */
    val controlBorder: Color = Color(0x33FFFFFF),

    /** High-contrast primary icon/text on viewfinder overlay. */
    val controlOnSurface: Color = Color(0xFFFFFFFF),

    /** Muted icon/text (70% white) on viewfinder overlay. */
    val controlOnSurfaceMuted: Color = Color(0xB3FFFFFF),

    /** High-contrast icon/text when placed on an active (gold) background. */
    val controlOnSurfaceActive: Color = Color(0xFF281800),

    /** Warm photographic gold accent for indicators (HDR active, Timer active, Zoom). */
    val activeAccent: Color = Color(0xFFFFB74D),

    /** Focus locked / horizon level matched color. */
    val focusSuccess: Color = Color(0xFF34D399),

    /** Horizon level tilt warning color. */
    val horizonWarning: Color = Color(0xFFFBBF24),

    /** Viewfinder rule-of-thirds grid line color. */
    val gridLine: Color = Color(0x40FFFFFF),

    /** Outer contrast ring of the shutter button. */
    val shutterOuterRing: Color = Color(0xFFFFFFFF),

    /** Inner core of the photo shutter button. */
    val shutterInnerCore: Color = Color(0xFFFFFFFF),

    /** Inner core of the video recording button. */
    val shutterRecordingCore: Color = Color(0xFFEF4444),
)

val LocalCameraOverlayColors = staticCompositionLocalOf { CameraOverlayColors() }
