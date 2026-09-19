package com.webappypie.optilens.core.ui.camera.histogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Compact real-time luminance histogram overlay for the camera viewfinder.
 */
@Composable
fun HistogramOverlay(
    data: HistogramData,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .width(96.dp)
            .height(44.dp)
            .clip(shape)
            .background(overlayColors.scrimBackground)
            .border(1.dp, overlayColors.controlBorder, shape)
            .padding(2.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bins = data.bins
            if (bins.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height
            val binWidth = w / bins.size.toFloat()

            val path = Path()
            path.moveTo(0f, h)

            for (i in bins.indices) {
                val binHeight = (bins[i].coerceIn(0f, 1f)) * (h - 2f)
                val x = i * binWidth
                val y = h - binHeight
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()

            // Fill with smooth gradient
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.15f),
                    ),
                    startY = 0f,
                    endY = h,
                ),
                style = Fill,
            )

            // Outline stroke
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.75f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
