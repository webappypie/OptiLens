package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.QualityMetrics
import kotlin.math.sqrt

/**
 * Computes optical quality metrics, focus/sharpness scores, dynamic range spread,
 * and backlight conditions from preprocessed viewfinder frames.
 */
class QualityMetricsEvaluator {

    /**
     * Evaluates [PreprocessedFrameData] to derive [QualityMetrics].
     */
    fun evaluate(data: PreprocessedFrameData): QualityMetrics {
        val width = data.gridWidth
        val height = data.gridHeight
        val yGrid = data.yGrid

        // 1. Compute Focus / Sharpness Score via discrete Laplacian operator
        var laplacianSum = 0.0
        var laplacianSqSum = 0.0
        var sampleCount = 0

        // Subsample interior pixels with step 2 for speed
        for (y in 2 until height - 2 step 2) {
            val rowOffset = y * width
            for (x in 2 until width - 2 step 2) {
                val center = yGrid[rowOffset + x]
                val left = yGrid[rowOffset + x - 1]
                val right = yGrid[rowOffset + x + 1]
                val top = yGrid[(y - 1) * width + x]
                val bottom = yGrid[(y + 1) * width + x]

                // Standard 5-point discrete Laplacian kernel
                val lap = (4 * center - left - right - top - bottom).toDouble()
                laplacianSum += lap
                laplacianSqSum += (lap * lap)
                sampleCount++
            }
        }

        val sharpnessScore = if (sampleCount > 0) {
            val mean = laplacianSum / sampleCount
            val variance = (laplacianSqSum / sampleCount) - (mean * mean)
            val stdDev = sqrt(variance.coerceAtLeast(0.0)).toFloat()
            val rms = sqrt((laplacianSqSum / sampleCount).coerceAtLeast(0.0)).toFloat()
            val response = maxOf(stdDev, rms)
            // Map typical response (0 to ~60) to a normalized 0..100 score
            ((response / 40.0f) * 100.0f).coerceIn(0.0f, 100.0f)
        } else {
            50.0f
        }

        // 2. Backlight detection
        val centerLum = data.centerLuminance
        val peripheryLum = data.peripheryLuminance
        val backlightRatio = peripheryLum / (centerLum.coerceAtLeast(1.0f))
        val isBacklit = (backlightRatio >= 1.6f && peripheryLum >= 120.0f && centerLum <= 110.0f) ||
                (backlightRatio >= 1.4f && data.highlightClippingPercent >= 2.0f && centerLum <= 90.0f)

        // 3. Dynamic Range estimate
        val nonZeroBins = data.histogramBins.count { it > 0.05f }
        val clippingSpread = (data.highlightClippingPercent + data.shadowClippingPercent).coerceAtMost(100f)
        val dynamicRangeScore = ((nonZeroBins / 48.0f) * 80.0f + (clippingSpread * 0.2f)).coerceIn(0.0f, 100.0f)

        return QualityMetrics(
            luminance = data.meanLuminance,
            highlightClippingPercent = data.highlightClippingPercent,
            shadowClippingPercent = data.shadowClippingPercent,
            sharpnessScore = sharpnessScore,
            isBacklit = isBacklit,
            backlightRatio = backlightRatio,
            dynamicRangeScore = dynamicRangeScore,
            timestampMs = data.timestampMs,
        )
    }
}
