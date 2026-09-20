package com.webappypie.optilens.core.ui.camera.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.camera.tracking.TrackedObjectState
import com.webappypie.optilens.core.camera.tracking.TrackingStatus
import kotlin.math.roundToInt

/**
 * Viewfinder overlay rendering real-time bounding reticle around a tracked object.
 *
 * Distinguishes:
 * - Cyan/Gold locked corner brackets during active confident tracking.
 * - Dashed amber brackets during short occlusion recovery.
 * - Dismiss button to cancel active tracking.
 */
@Composable
fun ObjectTrackingOverlay(
    trackedObjectState: TrackedObjectState,
    onDismissTracking: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF64FFDA), // Cyan tracking lock
    occludedColor: Color = Color(0xFFFFB74D), // Amber occlusion warning
) {
    val status = trackedObjectState.status
    val bounds = trackedObjectState.bounds

    if (!status.isVisualActive || bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
        return
    }

    val isOccluded = status == TrackingStatus.OCCLUDED
    val reticleColor = if (isOccluded) occludedColor else activeColor

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val left = bounds.left * canvasWidth
            val top = bounds.top * canvasHeight
            val right = bounds.right * canvasWidth
            val bottom = bounds.bottom * canvasHeight

            val boxWidth = right - left
            val boxHeight = bottom - top

            if (boxWidth > 8f && boxHeight > 8f) {
                val strokeWidth = 2.0.dp.toPx()
                val cornerLength = 16.dp.toPx()
                val armX = cornerLength.coerceAtMost(boxWidth * 0.35f)
                val armY = cornerLength.coerceAtMost(boxHeight * 0.35f)

                val pathEffect = if (isOccluded) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = pathEffect)

                val path = Path().apply {
                    // Top-Left
                    moveTo(left, top + armY)
                    lineTo(left, top)
                    lineTo(left + armX, top)

                    // Top-Right
                    moveTo(right - armX, top)
                    lineTo(right, top)
                    lineTo(right, top + armY)

                    // Bottom-Right
                    moveTo(right, bottom - armY)
                    lineTo(right, bottom)
                    lineTo(right - armX, bottom)

                    // Bottom-Left
                    moveTo(left + armX, bottom)
                    lineTo(left, bottom)
                    lineTo(left, bottom - armY)
                }

                drawPath(path = path, color = reticleColor, style = stroke)

                // Center crosshair tick
                val cx = (left + right) / 2.0f
                val cy = (top + bottom) / 2.0f
                val tickLen = 5.dp.toPx()
                drawLine(reticleColor, Offset(cx - tickLen, cy), Offset(cx + tickLen, cy), strokeWidth)
                drawLine(reticleColor, Offset(cx, cy - tickLen), Offset(cx, cy + tickLen), strokeWidth)
            }
        }

        // Small dismiss indicator on top-right corner of the tracked box
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (bounds.right * 1000).roundToInt(), // scaled in compose layout
                        y = (bounds.top * 1000).roundToInt(),
                    )
                }
                .padding(4.dp)
        )
    }
}
