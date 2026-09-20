package com.webappypie.optilens.core.ui.camera.histogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.camera.model.HistogramMode
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Compact real-time Luminance and RGB histogram overlay for the camera viewfinder.
 * Tapping the overlay cycles between Luminance -> RGB -> Both modes.
 */
@Composable
fun HistogramOverlay(
    data: HistogramData,
    modifier: Modifier = Modifier,
    mode: HistogramMode = HistogramMode.LUMINANCE,
    onToggleMode: (() -> Unit)? = null,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .width(104.dp)
            .height(48.dp)
            .clip(shape)
            .background(overlayColors.scrimBackground)
            .border(1.dp, overlayColors.controlBorder, shape)
            .clickable(enabled = onToggleMode != null, onClick = { onToggleMode?.invoke() })
            .padding(2.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (mode) {
                HistogramMode.LUMINANCE -> {
                    drawHistogramCurve(
                        bins = data.lumaBins,
                        fillColor = Color.White.copy(alpha = 0.35f),
                        strokeColor = Color.White.copy(alpha = 0.85f),
                        w = w,
                        h = h,
                    )
                }
                HistogramMode.RGB -> {
                    // Red channel
                    drawHistogramCurve(
                        bins = data.redBins,
                        fillColor = Color(0xFFFF4444).copy(alpha = 0.25f),
                        strokeColor = Color(0xFFFF5555).copy(alpha = 0.8f),
                        w = w,
                        h = h,
                    )
                    // Green channel
                    drawHistogramCurve(
                        bins = data.greenBins,
                        fillColor = Color(0xFF44FF44).copy(alpha = 0.25f),
                        strokeColor = Color(0xFF55FF55).copy(alpha = 0.8f),
                        w = w,
                        h = h,
                    )
                    // Blue channel
                    drawHistogramCurve(
                        bins = data.blueBins,
                        fillColor = Color(0xFF4488FF).copy(alpha = 0.25f),
                        strokeColor = Color(0xFF5599FF).copy(alpha = 0.8f),
                        w = w,
                        h = h,
                    )
                }
                HistogramMode.BOTH -> {
                    drawHistogramCurve(
                        bins = data.redBins,
                        fillColor = Color(0xFFFF4444).copy(alpha = 0.15f),
                        strokeColor = Color(0xFFFF5555).copy(alpha = 0.6f),
                        w = w,
                        h = h,
                    )
                    drawHistogramCurve(
                        bins = data.greenBins,
                        fillColor = Color(0xFF44FF44).copy(alpha = 0.15f),
                        strokeColor = Color(0xFF55FF55).copy(alpha = 0.6f),
                        w = w,
                        h = h,
                    )
                    drawHistogramCurve(
                        bins = data.blueBins,
                        fillColor = Color(0xFF4488FF).copy(alpha = 0.15f),
                        strokeColor = Color(0xFF5599FF).copy(alpha = 0.6f),
                        w = w,
                        h = h,
                    )
                    // Overlay white luminance outline on top
                    drawHistogramCurve(
                        bins = data.lumaBins,
                        fillColor = Color.Transparent,
                        strokeColor = Color.White.copy(alpha = 0.9f),
                        w = w,
                        h = h,
                    )
                }
            }
        }

        // Mode badge in top-left
        Text(
            text = mode.label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = overlayColors.controlOnSurface.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 2.dp),
        )
    }
}

private fun DrawScope.drawHistogramCurve(
    bins: FloatArray,
    fillColor: Color,
    strokeColor: Color,
    w: Float,
    h: Float,
) {
    if (bins.isEmpty()) return
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

    if (fillColor != Color.Transparent) {
        drawPath(
            path = path,
            color = fillColor,
            style = Fill,
        )
    }

    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(width = 1.dp.toPx()),
    )
}
