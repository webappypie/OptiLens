package com.webappypie.optilens.core.ui.review

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance interactive Before/After split comparison viewer.
 *
 * Enforces strictly identical crop, orientation, aspect ratio, and synchronized
 * pan/zoom between Original and Enhanced images.
 */
@Composable
fun BeforeAfterSplitViewer(
    originalBitmap: Bitmap,
    enhancedBitmap: Bitmap?,
    splitFraction: Float,
    onSplitFractionChanged: (Float) -> Unit,
    isHoldingToCompare: Boolean,
    onHoldingToCompareChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    val originalImageBitmap = remember(originalBitmap) { originalBitmap.asImageBitmap() }
    val enhancedImageBitmap = remember(enhancedBitmap) { enhancedBitmap?.asImageBitmap() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                containerWidth = it.width.toFloat()
                containerHeight = it.height.toFloat()
            }
            .pointerInput(Unit) {
                // Long-press gesture for "Hold to Compare"
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scale = if (scale > 1.2f) 1f else 2.5f
                        panOffset = if (scale == 1f) Offset.Zero else -tapOffset * (scale - 1f) / scale
                    },
                    onPress = {
                        if (enhancedBitmap != null) {
                            onHoldingToCompareChanged(true)
                            tryAwaitRelease()
                            onHoldingToCompareChanged(false)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // Synchronized Pinch-to-zoom and Pan
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale

                    if (newScale > 1f && containerWidth > 0f && containerHeight > 0f) {
                        val maxPanX = (containerWidth * (newScale - 1f)) / 2f
                        val maxPanY = (containerHeight * (newScale - 1f)) / 2f
                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    } else {
                        panOffset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        if (containerWidth > 0 && containerHeight > 0) {
            val splitPx = containerWidth * splitFraction

            // Drawing canvas rendering both images with identical crop and geometry
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val imgW = originalBitmap.width.toFloat()
                val imgH = originalBitmap.height.toFloat()
                val imgAspect = imgW / imgH
                val viewAspect = canvasWidth / canvasHeight

                val drawW: Float
                val drawH: Float
                val drawLeft: Float
                val drawTop: Float

                if (imgAspect > viewAspect) {
                    drawW = canvasWidth
                    drawH = canvasWidth / imgAspect
                    drawLeft = 0f
                    drawTop = (canvasHeight - drawH) / 2f
                } else {
                    drawH = canvasHeight
                    drawW = canvasHeight * imgAspect
                    drawLeft = (canvasWidth - drawW) / 2f
                    drawTop = 0f
                }

                val dstRect = Rect(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)
                val srcSize = IntSize(originalBitmap.width, originalBitmap.height)

                if (enhancedImageBitmap == null || isHoldingToCompare) {
                    // Render 100% Original
                    drawImage(
                        image = originalImageBitmap,
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                        dstOffset = androidx.compose.ui.unit.IntOffset(drawLeft.roundToInt(), drawTop.roundToInt())
                    )
                } else {
                    // 1. Draw Original on Left clipped to [0, splitPx]
                    clipRect(left = 0f, top = 0f, right = splitPx, bottom = canvasHeight) {
                        drawImage(
                            image = originalImageBitmap,
                            dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                            dstOffset = androidx.compose.ui.unit.IntOffset(drawLeft.roundToInt(), drawTop.roundToInt())
                        )
                    }

                    // 2. Draw Enhanced on Right clipped to [splitPx, canvasWidth]
                    clipRect(left = splitPx, top = 0f, right = canvasWidth, bottom = canvasHeight) {
                        drawImage(
                            image = enhancedImageBitmap,
                            dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                            dstOffset = androidx.compose.ui.unit.IntOffset(drawLeft.roundToInt(), drawTop.roundToInt())
                        )
                    }

                    // 3. Draw Split divider line
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(splitPx, 0f),
                        end = Offset(splitPx, canvasHeight),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Split slider draggable handle
            if (enhancedBitmap != null && !isHoldingToCompare && scale <= 1.05f) {
                val handleSize = 44.dp
                val handleSizePx = with(LocalDensity.current) { handleSize.toPx() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (splitPx - handleSizePx / 2f).roundToInt(),
                                y = (containerHeight / 2f - handleSizePx / 2f).roundToInt()
                            )
                        }
                        .size(handleSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                val newFraction = (splitFraction + delta / containerWidth).coerceIn(0.02f, 0.98f)
                                onSplitFractionChanged(newFraction)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "◀ ▶",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // Floating Badges: BEFORE / AFTER
        AnimatedVisibility(
            visible = enhancedBitmap != null && !isHoldingToCompare,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "BEFORE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        AnimatedVisibility(
            visible = enhancedBitmap != null && !isHoldingToCompare,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "AFTER",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // "Hold to Compare" notice badge
        AnimatedVisibility(
            visible = isHoldingToCompare,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Showing Original (Hold to Compare)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
