package com.webappypie.optilens.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun `min touch target is at least 48dp for accessibility`() {
        val iconSizes = OptiLensIconSizes()
        assertTrue(
            "Touch target must be at least 48dp according to accessibility standards",
            iconSizes.minTouchTarget >= 48.dp,
        )
    }

    @Test
    fun `spacing follows 4dp layout grid progression`() {
        val spacing = OptiLensSpacing()
        assertEquals(0.dp, spacing.none)
        assertEquals(2.dp, spacing.xxs)
        assertEquals(4.dp, spacing.xs)
        assertEquals(8.dp, spacing.s)
        assertEquals(12.dp, spacing.m)
        assertEquals(16.dp, spacing.l)
        assertEquals(20.dp, spacing.xl)
        assertEquals(24.dp, spacing.xxl)
        assertEquals(32.dp, spacing.xxxl)
        assertEquals(40.dp, spacing.huge)
        assertEquals(48.dp, spacing.massive)
        assertEquals(64.dp, spacing.giant)
    }

    @Test
    fun `camera overlay colors provide required scrim and active contrast`() {
        val overlay = CameraOverlayColors()
        // Scrim background must be semi-transparent black
        assertTrue(overlay.scrimBackground.alpha > 0.4f)
        // Control on surface must be pure white for high contrast over dark viewfinder
        assertEquals(1.0f, overlay.controlOnSurface.red)
        assertEquals(1.0f, overlay.controlOnSurface.green)
        assertEquals(1.0f, overlay.controlOnSurface.blue)
    }

    @Test
    fun `typography provides tabular monospace numbers for camera readouts`() {
        val zoomStyle = OptiLensCameraTypography.zoomReadout
        val shutterStyle = OptiLensCameraTypography.shutterSpeed
        val proStyle = OptiLensCameraTypography.proValue

        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, zoomStyle.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, shutterStyle.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, proStyle.fontFamily)
    }
}
