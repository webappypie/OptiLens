package com.webappypie.optilens.core.ui.camera.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.camera.model.DetectedFace

/**
 * Renders subtle, elegant corner bracket indicators around detected faces.
 *
 * Adheres to OptiLens design principles: unobtrusive, fine 1.5dp strokes,
 * warm photographic accent tint, and non-blocking pointer events.
 */
@Composable
fun FaceBoundingBoxOverlay(
    faces: List<DetectedFace>,
    modifier: Modifier = Modifier,
    bracketColor: Color = Color(0xFFE5C07B).copy(alpha = 0.85f),
    landmarkColor: Color = Color.White.copy(alpha = 0.60f),
) {
    if (faces.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val strokeWidth = 1.75.dp.toPx()
        val cornerLength = 14.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        for (face in faces) {
            val bounds = face.bounds
            val left = bounds.left * canvasWidth
            val top = bounds.top * canvasHeight
            val right = bounds.right * canvasWidth
            val bottom = bounds.bottom * canvasHeight

            val boxWidth = right - left
            val boxHeight = bottom - top

            if (boxWidth <= 10f || boxHeight <= 10f) continue

            val armX = cornerLength.coerceAtMost(boxWidth * 0.3f)
            val armY = cornerLength.coerceAtMost(boxHeight * 0.3f)

            val path = Path().apply {
                // Top-Left corner
                moveTo(left, top + armY)
                lineTo(left, top)
                lineTo(left + armX, top)

                // Top-Right corner
                moveTo(right - armX, top)
                lineTo(right, top)
                lineTo(right, top + armY)

                // Bottom-Right corner
                moveTo(right, bottom - armY)
                lineTo(right, bottom)
                lineTo(right - armX, bottom)

                // Bottom-Left corner
                moveTo(left + armX, bottom)
                lineTo(left, bottom)
                lineTo(left, bottom - armY)
            }

            drawPath(path = path, color = bracketColor, style = stroke)

            // Subtle landmark points
            for (landmark in face.landmarks) {
                val lx = landmark.x * canvasWidth
                val ly = landmark.y * canvasHeight
                drawCircle(
                    color = landmarkColor,
                    radius = 2.0.dp.toPx(),
                    center = Offset(lx, ly),
                )
            }
        }
    }
}
