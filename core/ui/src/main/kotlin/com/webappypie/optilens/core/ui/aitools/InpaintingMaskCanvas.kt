package com.webappypie.optilens.core.ui.aitools

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

data class BrushStroke(
    val points: List<Offset>,
    val strokeWidth: Float,
)

/**
 * Interactive touch canvas for highlighting objects or distractions to inpaint.
 */
@Composable
fun InpaintingMaskCanvas(
    brushSize: Float,
    strokes: MutableList<BrushStroke>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var currentPoints = remember { mutableStateListOf<Offset>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPoints.clear()
                        currentPoints.add(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoints.add(change.position)
                    },
                    onDragEnd = {
                        if (currentPoints.isNotEmpty()) {
                            strokes.add(BrushStroke(currentPoints.toList(), brushSize))
                            currentPoints.clear()
                        }
                    },
                    onDragCancel = {
                        currentPoints.clear()
                    }
                )
            }
    ) {
        // Draw confirmed strokes
        for (stroke in strokes) {
            if (stroke.points.size > 1) {
                val path = Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                drawPath(
                    path = path,
                    color = Color(0xFFFF5252).copy(alpha = 0.50f), // Translucent red mask
                    style = Stroke(
                        width = stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                )
            } else if (stroke.points.size == 1) {
                drawCircle(
                    color = Color(0xFFFF5252).copy(alpha = 0.50f),
                    radius = stroke.strokeWidth / 2f,
                    center = stroke.points[0],
                )
            }
        }

        // Draw active in-progress stroke
        if (currentPoints.size > 1) {
            val path = Path()
            path.moveTo(currentPoints[0].x, currentPoints[0].y)
            for (i in 1 until currentPoints.size) {
                path.lineTo(currentPoints[i].x, currentPoints[i].y)
            }
            drawPath(
                path = path,
                color = Color(0xFFFF5252).copy(alpha = 0.65f),
                style = Stroke(
                    width = brushSize,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
            )
        } else if (currentPoints.size == 1) {
            drawCircle(
                color = Color(0xFFFF5252).copy(alpha = 0.65f),
                radius = brushSize / 2f,
                center = currentPoints[0],
            )
        }
    }
}

/**
 * Converts user screen strokes into an exact binary mask Bitmap matching target dimensions.
 */
fun generateMaskBitmapFromStrokes(
    strokes: List<BrushStroke>,
    targetWidth: Int,
    targetHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float,
): Bitmap {
    val mask = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    if (strokes.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) return mask

    val scaleX = targetWidth.toFloat() / canvasWidth
    val scaleY = targetHeight.toFloat() / canvasHeight

    val androidCanvas = android.graphics.Canvas(mask)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    for (stroke in strokes) {
        paint.strokeWidth = stroke.strokeWidth * ((scaleX + scaleY) * 0.5f)
        if (stroke.points.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(stroke.points[0].x * scaleX, stroke.points[0].y * scaleY)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x * scaleX, stroke.points[i].y * scaleY)
            }
            androidCanvas.drawPath(path, paint)
        } else if (stroke.points.size == 1) {
            androidCanvas.drawCircle(
                stroke.points[0].x * scaleX,
                stroke.points[0].y * scaleY,
                paint.strokeWidth / 2f,
                paint.apply { style = android.graphics.Paint.Style.FILL }
            )
            paint.style = android.graphics.Paint.Style.STROKE
        }
    }

    return mask
}
