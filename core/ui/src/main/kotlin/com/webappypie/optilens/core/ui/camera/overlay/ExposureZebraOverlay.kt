package com.webappypie.optilens.core.ui.camera.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.camera.model.ExposureZebraData

/**
 * Viewfinder overlay displaying diagonal zebra hazard stripes over overexposed clipped highlight regions.
 */
@Composable
fun ExposureZebraOverlay(
    data: ExposureZebraData,
    modifier: Modifier = Modifier,
    stripeColor: Color = Color(0xFFFFCC00), // Safety hazard yellow
) {
    if (!data.isEnabled || data.clippedRegions.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val points = data.clippedRegions
        val w = size.width
        val h = size.height
        val halfStripe = 6.dp.toPx()
        val strokeWidthPx = 1.5f.dp.toPx()

        val count = points.size - 1
        var i = 0
        while (i < count) {
            val px = points[i] * w
            val py = points[i + 1] * h

            // Draw 45-degree diagonal hazard zebra stripe
            drawLine(
                color = stripeColor,
                start = Offset(px - halfStripe, py + halfStripe),
                end = Offset(px + halfStripe, py - halfStripe),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
            i += 2
        }
    }
}
