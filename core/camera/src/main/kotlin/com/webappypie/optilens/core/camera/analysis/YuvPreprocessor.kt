package com.webappypie.optilens.core.camera.analysis

import androidx.camera.core.ImageProxy

/**
 * Preprocessed downsampled frame metrics, chrominance distributions, and Pro assistance data.
 */
data class PreprocessedFrameData(
    val gridWidth: Int,
    val gridHeight: Int,
    val yGrid: IntArray,
    val histogramBins: FloatArray,
    val meanLuminance: Float,
    val centerLuminance: Float,
    val peripheryLuminance: Float,
    val highlightClippingPercent: Float,
    val shadowClippingPercent: Float,
    val averageU: Float,
    val averageV: Float,
    val skyScore: Float,
    val plantScore: Float,
    val warmScore: Float,
    val timestampMs: Long,
    val redHistogramBins: FloatArray = FloatArray(64),
    val greenHistogramBins: FloatArray = FloatArray(64),
    val blueHistogramBins: FloatArray = FloatArray(64),
    val focusPeakingPoints: FloatArray = FloatArray(0),
    val exposureZebraRegions: FloatArray = FloatArray(0),
)

/**
 * High-performance, zero-allocation preprocessor for CameraX YUV_420_888 [ImageProxy] frames.
 *
 * Subsamples the full-resolution Y-plane into a fixed (160x120) luminance grid,
 * constructs 64-bin normalized Luminance and RGB histograms, samples the U/V planes to extract
 * chrominance signatures for scene analysis, and extracts focus peaking edges and exposure clipping.
 */
class YuvPreprocessor(
    val gridWidth: Int = 160,
    val gridHeight: Int = 120,
) {
    private val yGrid = IntArray(gridWidth * gridHeight)
    private val rawHistogram = IntArray(64)
    private val normalizedHistogram = FloatArray(64)

    private val rawRedHistogram = IntArray(64)
    private val rawGreenHistogram = IntArray(64)
    private val rawBlueHistogram = IntArray(64)
    private val normalizedRedHistogram = FloatArray(64)
    private val normalizedGreenHistogram = FloatArray(64)
    private val normalizedBlueHistogram = FloatArray(64)

    // Reusable line buffer to minimize JNI ByteBuffer overhead
    private var cachedRowStride = 0
    private var rowBuffer = ByteArray(0)

    /**
     * Extracts downsampled luminance, spatial regions, RGB channels, focus edges, and chromaticity.
     */
    fun process(image: ImageProxy): PreprocessedFrameData {
        val planes = image.planes
        if (planes.isEmpty()) {
            return createEmptyData()
        }

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val imgWidth = image.width
        val imgHeight = image.height

        if (yRowStride != cachedRowStride) {
            cachedRowStride = yRowStride
            rowBuffer = ByteArray(yRowStride)
        }

        rawHistogram.fill(0)
        rawRedHistogram.fill(0)
        rawGreenHistogram.fill(0)
        rawBlueHistogram.fill(0)

        var totalLuminanceSum = 0L
        var centerLuminanceSum = 0L
        var centerPixelCount = 0
        var peripheryLuminanceSum = 0L
        var peripheryPixelCount = 0
        var highlightCount = 0
        var shadowCount = 0

        val stepX = (imgWidth / gridWidth).coerceAtLeast(1)
        val stepY = (imgHeight / gridHeight).coerceAtLeast(1)

        val centerMinX = (gridWidth * 0.3f).toInt()
        val centerMaxX = (gridWidth * 0.7f).toInt()
        val centerMinY = (gridHeight * 0.3f).toInt()
        val centerMaxY = (gridHeight * 0.7f).toInt()

        for (gy in 0 until gridHeight) {
            val srcY = (gy * stepY).coerceAtMost(imgHeight - 1)
            val rowOffset = srcY * yRowStride
            yBuffer.position(rowOffset)
            val bytesToRead = minOf(yRowStride, yBuffer.remaining())
            yBuffer.get(rowBuffer, 0, bytesToRead)

            for (gx in 0 until gridWidth) {
                val srcX = (gx * stepX).coerceAtMost(imgWidth - 1)
                val pixelIndex = srcX * yPixelStride
                val lum = if (pixelIndex < bytesToRead) {
                    rowBuffer[pixelIndex].toInt() and 0xFF
                } else 128

                val gridIdx = gy * gridWidth + gx
                yGrid[gridIdx] = lum
                totalLuminanceSum += lum

                val bin = (lum shr 2).coerceIn(0, 63)
                rawHistogram[bin]++

                if (lum >= 242) highlightCount++
                if (lum <= 10) shadowCount++

                val isCenter = gx in centerMinX..centerMaxX && gy in centerMinY..centerMaxY
                val isPeriphery = gx < (gridWidth * 0.15f) || gx > (gridWidth * 0.85f) ||
                        gy < (gridHeight * 0.15f) || gy > (gridHeight * 0.85f)

                if (isCenter) {
                    centerLuminanceSum += lum
                    centerPixelCount++
                }
                if (isPeriphery) {
                    peripheryLuminanceSum += lum
                    peripheryPixelCount++
                }
            }
        }

        val totalPixels = gridWidth * gridHeight
        var maxBin = 1
        for (bin in rawHistogram) {
            if (bin > maxBin) maxBin = bin
        }
        for (i in 0 until 64) {
            normalizedHistogram[i] = rawHistogram[i].toFloat() / maxBin.toFloat()
        }

        val meanLum = totalLuminanceSum.toFloat() / totalPixels.toFloat()
        val centerLum = if (centerPixelCount > 0) centerLuminanceSum.toFloat() / centerPixelCount else meanLum
        val peripheryLum = if (peripheryPixelCount > 0) peripheryLuminanceSum.toFloat() / peripheryPixelCount else meanLum
        val highlightPct = (highlightCount.toFloat() / totalPixels) * 100f
        val shadowPct = (shadowCount.toFloat() / totalPixels) * 100f

        // Chrominance statistics & RGB conversion from U and V planes
        var uSum = 0L
        var vSum = 0L
        var skyPixelVotes = 0
        var plantPixelVotes = 0
        var warmPixelVotes = 0
        var uvSampleCount = 0

        if (planes.size >= 3) {
            val uPlane = planes[1]
            val vPlane = planes[2]
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val uvGridW = 40
            val uvGridH = 30
            val uvStepX = (imgWidth / (uvGridW * 2)).coerceAtLeast(1)
            val uvStepY = (imgHeight / (uvGridH * 2)).coerceAtLeast(1)

            val maxUBuf = uBuffer.remaining()
            val maxVBuf = vBuffer.remaining()

            for (uy in 0 until uvGridH) {
                val srcY = (uy * uvStepY)
                val rowOffset = srcY * uvRowStride

                for (ux in 0 until uvGridW) {
                    val srcX = (ux * uvStepX)
                    val offset = rowOffset + srcX * uvPixelStride

                    if (offset < maxUBuf && offset < maxVBuf) {
                        val uVal = uBuffer.get(offset).toInt() and 0xFF
                        val vVal = vBuffer.get(offset).toInt() and 0xFF
                        uSum += uVal
                        vSum += vVal
                        uvSampleCount++

                        // Sky: High U (blue > neutral 128), lower V (< neutral 128), top 50%
                        if (uy < uvGridH / 2 && uVal > 138 && vVal < 126) {
                            skyPixelVotes++
                        }
                        // Plant/Foliage: Green dominance corresponds to negative (V - 128)
                        if (vVal < 118 && uVal < 125) {
                            plantPixelVotes++
                        }
                        // Warm: High V (red), moderate U
                        if (vVal > 135 && uVal < 128) {
                            warmPixelVotes++
                        }

                        // Approximate corresponding Y value for RGB channel histogramming
                        val gy = (uy * gridHeight / uvGridH).coerceIn(0, gridHeight - 1)
                        val gx = (ux * gridWidth / uvGridW).coerceIn(0, gridWidth - 1)
                        val yVal = yGrid[gy * gridWidth + gx]

                        // Standard BT.601 integer RGB conversion
                        val c = yVal - 16
                        val d = uVal - 128
                        val e = vVal - 128
                        val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                        val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                        val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                        rawRedHistogram[(r shr 2).coerceIn(0, 63)]++
                        rawGreenHistogram[(g shr 2).coerceIn(0, 63)]++
                        rawBlueHistogram[(b shr 2).coerceIn(0, 63)]++
                    }
                }
            }
        }

        // Normalize RGB histograms
        var maxR = 1
        var maxG = 1
        var maxB = 1
        for (i in 0 until 64) {
            if (rawRedHistogram[i] > maxR) maxR = rawRedHistogram[i]
            if (rawGreenHistogram[i] > maxG) maxG = rawGreenHistogram[i]
            if (rawBlueHistogram[i] > maxB) maxB = rawBlueHistogram[i]
        }
        for (i in 0 until 64) {
            normalizedRedHistogram[i] = rawRedHistogram[i].toFloat() / maxR.toFloat()
            normalizedGreenHistogram[i] = rawGreenHistogram[i].toFloat() / maxG.toFloat()
            normalizedBlueHistogram[i] = rawBlueHistogram[i].toFloat() / maxB.toFloat()
        }

        val avgU = if (uvSampleCount > 0) uSum.toFloat() / uvSampleCount else 128f
        val avgV = if (uvSampleCount > 0) vSum.toFloat() / uvSampleCount else 128f
        val skyScore = if (uvSampleCount > 0) (skyPixelVotes.toFloat() / (uvSampleCount / 2f)).coerceIn(0f, 1f) else 0f
        val plantScore = if (uvSampleCount > 0) (plantPixelVotes.toFloat() / uvSampleCount).coerceIn(0f, 1f) else 0f
        val warmScore = if (uvSampleCount > 0) (warmPixelVotes.toFloat() / uvSampleCount).coerceIn(0f, 1f) else 0f

        // Focus Peaking (spatial gradient) and Exposure Zebra (highlight clipping)
        val peakingList = ArrayList<Float>(240)
        val zebraList = ArrayList<Float>(240)

        for (gy in 1 until gridHeight - 1 step 2) {
            val row = gy * gridWidth
            val prevRow = (gy - 1) * gridWidth
            val nextRow = (gy + 1) * gridWidth
            val normY = gy.toFloat() / gridHeight.toFloat()

            for (gx in 1 until gridWidth - 1 step 2) {
                val center = yGrid[row + gx]
                val left = yGrid[row + gx - 1]
                val right = yGrid[row + gx + 1]
                val top = yGrid[prevRow + gx]
                val bottom = yGrid[nextRow + gx]

                val grad = kotlin.math.abs(4 * center - left - right - top - bottom)
                if (grad > 48 && peakingList.size < 400) {
                    peakingList.add(gx.toFloat() / gridWidth.toFloat())
                    peakingList.add(normY)
                }

                if (center >= 242 && zebraList.size < 400) {
                    zebraList.add(gx.toFloat() / gridWidth.toFloat())
                    zebraList.add(normY)
                }
            }
        }

        return PreprocessedFrameData(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            yGrid = yGrid.clone(),
            histogramBins = normalizedHistogram.clone(),
            meanLuminance = meanLum,
            centerLuminance = centerLum,
            peripheryLuminance = peripheryLum,
            highlightClippingPercent = highlightPct,
            shadowClippingPercent = shadowPct,
            averageU = avgU,
            averageV = avgV,
            skyScore = skyScore,
            plantScore = plantScore,
            warmScore = warmScore,
            timestampMs = System.currentTimeMillis(),
            redHistogramBins = normalizedRedHistogram.clone(),
            greenHistogramBins = normalizedGreenHistogram.clone(),
            blueHistogramBins = normalizedBlueHistogram.clone(),
            focusPeakingPoints = peakingList.toFloatArray(),
            exposureZebraRegions = zebraList.toFloatArray(),
        )
    }

    private fun createEmptyData(): PreprocessedFrameData {
        return PreprocessedFrameData(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            yGrid = IntArray(gridWidth * gridHeight) { 128 },
            histogramBins = FloatArray(64) { 0f },
            meanLuminance = 128f,
            centerLuminance = 128f,
            peripheryLuminance = 128f,
            highlightClippingPercent = 0f,
            shadowClippingPercent = 0f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0f,
            plantScore = 0f,
            warmScore = 0f,
            timestampMs = System.currentTimeMillis(),
            redHistogramBins = FloatArray(64) { 0f },
            greenHistogramBins = FloatArray(64) { 0f },
            blueHistogramBins = FloatArray(64) { 0f },
            focusPeakingPoints = FloatArray(0),
            exposureZebraRegions = FloatArray(0),
        )
    }
}
