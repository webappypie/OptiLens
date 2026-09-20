package com.webappypie.optilens.core.ui.camera.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.camera.model.FocusPeakingData

/**
 * Viewfinder overlay displaying high-contrast neon peaking highlights over planes of focus.
 */
@Composable
fun FocusPeakingOverlay(
    data: FocusPeakingData,
    modifier: Modifier = Modifier,
    peakingColor: Color = Color(0xFF00FF66), // Vivid neon green
) {
    if (!data.isEnabled || data.edgePoints.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val points = data.edgePoints
        val w = size.width
        val h = size.height
        val radiusPx = 2.dp.toPx()

        val count = points.size - 1
        var i = 0
        while (i < count) {
            val px = points[i] * w
            val py = points[i + 1] * h
            drawCircle(
                color = peakingColor,
                radius = radiusPx,
                center = Offset(px, py),
            )
            i += 2
        }
    }
}
