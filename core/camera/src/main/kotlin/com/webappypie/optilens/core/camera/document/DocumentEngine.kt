package com.webappypie.optilens.core.camera.document

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Rectified multi-plane document buffer produced by [DocumentEngine].
 */
data class ProcessedDocumentBuffer(
    val yPlane: ByteArray,
    val uPlane: ByteArray?,
    val vPlane: ByteArray?,
    val width: Int,
    val height: Int,
    val colorMode: DocumentColorMode,
    val detectedQuad: DocumentQuad,
)

/**
 * Full-pipeline engine for Document scanning, perspective rectification, and enhancement.
 *
 * Implements:
 * 1. Page Detection: Boundary edge gradient analysis identifying document corners.
 * 2. Perspective Correction: 4-point bilinear mapping to flat rectangular scan.
 * 3. Illumination Normalization: Background shading flattening to eliminate hand/phone shadows.
 * 4. Readable Sharpening: High-pass text edge sharpening for crisp character strokes.
 * 5. Color Modes: Full Color, clean Grayscale, and adaptive B&W binarization.
 */
@Singleton
class DocumentEngine @Inject constructor() {

    /**
     * Detects the bounding quadrilateral of a document on a downsampled luminance grid.
     */
    fun detectPageQuad(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        stride: Int = width,
    ): DocumentQuad {
        if (yPlane.isEmpty() || width < 10 || height < 10) {
            return DocumentQuad.DEFAULT
        }

        // Subsample into coarse 32x24 grid to find page boundaries
        val gridW = 32
        val gridH = 24
        val stepX = (width / gridW).coerceAtLeast(1)
        val stepY = (height / gridH).coerceAtLeast(1)

        val grid = IntArray(gridW * gridH)
        for (gy in 0 until gridH) {
            val sy = (gy * stepY).coerceAtMost(height - 1)
            val row = sy * stride
            for (gx in 0 until gridW) {
                val sx = (gx * stepX).coerceAtMost(width - 1)
                grid[gy * gridW + gx] = yPlane[row + sx].toInt() and 0xFF
            }
        }

        // Evaluate contrast gradients from outer edges toward center
        var minX = 1
        var maxX = gridW - 2
        var minY = 1
        var maxY = gridH - 2

        val centerLuma = grid[(gridH / 2) * gridW + (gridW / 2)]

        // Find left boundary (first significant step where luma approaches page center)
        for (x in 0 until gridW / 3) {
            val avg = (0 until gridH).sumOf { grid[it * gridW + x] } / gridH
            if (abs(avg - centerLuma) < 35 && avg > 80) {
                minX = x
                break
            }
        }

        // Find right boundary
        for (x in gridW - 1 downTo (gridW * 2) / 3) {
            val avg = (0 until gridH).sumOf { grid[it * gridW + x] } / gridH
            if (abs(avg - centerLuma) < 35 && avg > 80) {
                maxX = x
                break
            }
        }

        // Find top boundary
        for (y in 0 until gridH / 3) {
            val avg = (0 until gridW).sumOf { grid[y * gridW + it] } / gridW
            if (abs(avg - centerLuma) < 35 && avg > 80) {
                minY = y
                break
            }
        }

        // Find bottom boundary
        for (y in gridH - 1 downTo (gridH * 2) / 3) {
            val avg = (0 until gridW).sumOf { grid[y * gridW + it] } / gridW
            if (abs(avg - centerLuma) < 35 && avg > 80) {
                maxY = y
                break
            }
        }

        val normLeft = (minX.toFloat() / gridW.toFloat()).coerceIn(0.02f, 0.30f)
        val normRight = (maxX.toFloat() / gridW.toFloat()).coerceIn(0.70f, 0.98f)
        val normTop = (minY.toFloat() / gridH.toFloat()).coerceIn(0.02f, 0.30f)
        val normBottom = (maxY.toFloat() / gridH.toFloat()).coerceIn(0.70f, 0.98f)

        val quad = DocumentQuad(
            topLeft = DocumentPoint(normLeft, normTop),
            topRight = DocumentPoint(normRight, normTop),
            bottomRight = DocumentPoint(normRight, normBottom),
            bottomLeft = DocumentPoint(normLeft, normBottom),
        )

        return if (quad.isValid) quad else DocumentQuad.DEFAULT
    }

    /**
     * Warps a quadrilateral region of [inputY] into an upright rectangular [outWidth]x[outHeight] buffer.
     * Uses bilinear interpolation across the 4 corners.
     */
    fun warpPerspective(
        inputY: ByteArray,
        inWidth: Int,
        inHeight: Int,
        quad: DocumentQuad,
        outWidth: Int = inWidth,
        outHeight: Int = inHeight,
    ): ByteArray {
        val outY = ByteArray(outWidth * outHeight)

        val tl = quad.topLeft
        val tr = quad.topRight
        val br = quad.bottomRight
        val bl = quad.bottomLeft

        val inWF = inWidth.toFloat()
        val inHF = inHeight.toFloat()

        for (oy in 0 until outHeight) {
            val v = oy.toFloat() / (outHeight - 1).toFloat()
            val row = oy * outWidth

            // Left and right edges at vertical fraction v
            val lx = bl.x * v + tl.x * (1f - v)
            val ly = bl.y * v + tl.y * (1f - v)
            val rx = br.x * v + tr.x * (1f - v)
            val ry = br.y * v + tr.y * (1f - v)

            for (ox in 0 until outWidth) {
                val u = ox.toFloat() / (outWidth - 1).toFloat()

                // Bilinear interpolated source coordinates in [0, 1]
                val srcNormX = rx * u + lx * (1f - u)
                val srcNormY = ry * u + ly * (1f - u)

                val srcPixelX = (srcNormX * inWF).toInt().coerceIn(0, inWidth - 1)
                val srcPixelY = (srcNormY * inHF).toInt().coerceIn(0, inHeight - 1)

                outY[row + ox] = inputY[srcPixelY * inWidth + srcPixelX]
            }
        }

        return outY
    }

    /**
     * Removes shadows (such as phone or hand shadows) by dividing by estimated background illumination.
     */
    fun normalizeIllumination(
        inputY: ByteArray,
        width: Int,
        height: Int,
    ): ByteArray {
        val outY = ByteArray(inputY.size)
        if (inputY.isEmpty() || width < 8 || height < 8) return inputY

        // Estimate background illumination using a downsampled 16x16 grid
        val bgGridW = 16
        val bgGridH = 16
        val bgGrid = IntArray(bgGridW * bgGridH)

        val stepX = width / bgGridW
        val stepY = height / bgGridH

        for (gy in 0 until bgGridH) {
            for (gx in 0 until bgGridW) {
                var sum = 0L
                var count = 0
                val startY = gy * stepY
                val endY = ((gy + 1) * stepY).coerceAtMost(height)
                val startX = gx * stepX
                val endX = ((gx + 1) * stepX).coerceAtMost(width)

                for (y in startY until endY step 2) {
                    val row = y * width
                    for (x in startX until endX step 2) {
                        sum += (inputY[row + x].toInt() and 0xFF)
                        count++
                    }
                }
                bgGrid[gy * bgGridW + gx] = if (count > 0) (sum / count).toInt().coerceAtLeast(30) else 128
            }
        }

        // Divide each pixel by interpolated background illumination
        for (y in 0 until height) {
            val gyF = (y.toFloat() / height.toFloat()) * (bgGridH - 1)
            val gy0 = gyF.toInt().coerceIn(0, bgGridH - 1)
            val gy1 = (gy0 + 1).coerceIn(0, bgGridH - 1)
            val fy = gyF - gy0
            val row = y * width

            for (x in 0 until width) {
                val gxF = (x.toFloat() / width.toFloat()) * (bgGridW - 1)
                val gx0 = gxF.toInt().coerceIn(0, bgGridW - 1)
                val gx1 = (gx0 + 1).coerceIn(0, bgGridW - 1)
                val fx = gxF - gx0

                val b00 = bgGrid[gy0 * bgGridW + gx0]
                val b10 = bgGrid[gy0 * bgGridW + gx1]
                val b01 = bgGrid[gy1 * bgGridW + gx0]
                val b11 = bgGrid[gy1 * bgGridW + gx1]

                val bTop = b10 * fx + b00 * (1f - fx)
                val bBot = b11 * fx + b01 * (1f - fx)
                val bgEstimated = (bBot * fy + bTop * (1f - fy)).coerceAtLeast(30f)

                val pixel = inputY[row + x].toInt() and 0xFF

                // Flatten illumination: scale pixel relative to local background brightness
                val normalized = ((pixel.toFloat() / bgEstimated) * 210f).toInt().coerceIn(0, 255)
                outY[row + x] = normalized.toByte()
            }
        }

        return outY
    }

    /**
     * Applies high-pass sharpening specifically tuned for character stroke readability.
     */
    fun applyReadableSharpening(
        inputY: ByteArray,
        width: Int,
        height: Int,
    ): ByteArray {
        val outY = ByteArray(inputY.size)
        if (inputY.isEmpty() || width < 3 || height < 3) return inputY

        // Copy borders
        for (x in 0 until width) {
            outY[x] = inputY[x]
            outY[(height - 1) * width + x] = inputY[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outY[y * width] = inputY[y * width]
            outY[y * width + width - 1] = inputY[y * width + width - 1]
        }

        // Discrete 3x3 unsharp kernel
        for (y in 1 until height - 1) {
            val row = y * width
            val prev = (y - 1) * width
            val next = (y + 1) * width

            for (x in 1 until width - 1) {
                val center = inputY[row + x].toInt() and 0xFF
                val top = inputY[prev + x].toInt() and 0xFF
                val bottom = inputY[next + x].toInt() and 0xFF
                val left = inputY[row + x - 1].toInt() and 0xFF
                val right = inputY[row + x + 1].toInt() and 0xFF

                // Laplacian edge
                val lap = (4 * center - top - bottom - left - right)
                val sharpened = (center + (lap * 0.45f).toInt()).coerceIn(0, 255)

                outY[row + x] = sharpened.toByte()
            }
        }

        return outY
    }

    /**
     * High-contrast adaptive binarization producing crisp black text on pure white background.
     */
    fun binarizeBlackAndWhite(
        inputY: ByteArray,
        width: Int,
        height: Int,
    ): ByteArray {
        val outY = ByteArray(inputY.size)
        if (inputY.isEmpty()) return outY

        // Compute global Otsu threshold approximation
        var sum = 0L
        val count = inputY.size
        for (i in 0 until count) {
            sum += (inputY[i].toInt() and 0xFF)
        }
        val meanLuma = (sum / count).toInt()
        val threshold = (meanLuma * 0.88f).toInt().coerceIn(60, 190)

        for (i in 0 until count) {
            val luma = inputY[i].toInt() and 0xFF
            outY[i] = if (luma < threshold) 0.toByte() else 255.toByte()
        }

        return outY
    }

    /**
     * Executes the full document rectification and processing pipeline.
     */
    fun processDocument(
        inputY: ByteArray,
        inputU: ByteArray?,
        inputV: ByteArray?,
        width: Int,
        height: Int,
        quad: DocumentQuad = detectPageQuad(inputY, width, height),
        colorMode: DocumentColorMode = DocumentColorMode.COLOR,
    ): ProcessedDocumentBuffer {
        // 1. Perspective warp
        val warpedY = warpPerspective(inputY, width, height, quad, width, height)

        // 2. Illumination normalization (shadow removal)
        val normalizedY = normalizeIllumination(warpedY, width, height)

        // 3. Text sharpening
        val sharpenedY = applyReadableSharpening(normalizedY, width, height)

        // 4. Color mode branch
        return when (colorMode) {
            DocumentColorMode.COLOR -> {
                // Return sharpened Y with warped U and V
                val warpedU = if (inputU != null) warpPerspective(inputU, width / 2, height / 2, quad, width / 2, height / 2) else null
                val warpedV = if (inputV != null) warpPerspective(inputV, width / 2, height / 2, quad, width / 2, height / 2) else null
                ProcessedDocumentBuffer(sharpenedY, warpedU, warpedV, width, height, colorMode, quad)
            }
            DocumentColorMode.GRAYSCALE -> {
                // Neutral U/V plane (128)
                val uvSize = (width / 2) * (height / 2)
                val neutralU = ByteArray(uvSize) { 128.toByte() }
                val neutralV = ByteArray(uvSize) { 128.toByte() }
                ProcessedDocumentBuffer(sharpenedY, neutralU, neutralV, width, height, colorMode, quad)
            }
            DocumentColorMode.BLACK_AND_WHITE -> {
                val binaryY = binarizeBlackAndWhite(sharpenedY, width, height)
                val uvSize = (width / 2) * (height / 2)
                val neutralU = ByteArray(uvSize) { 128.toByte() }
                val neutralV = ByteArray(uvSize) { 128.toByte() }
                ProcessedDocumentBuffer(binaryY, neutralU, neutralV, width, height, colorMode, quad)
            }
        }
    }
}
