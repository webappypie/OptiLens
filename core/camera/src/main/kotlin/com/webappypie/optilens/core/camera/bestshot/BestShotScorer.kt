package com.webappypie.optilens.core.camera.bestshot

import com.webappypie.optilens.core.camera.model.DetectedFace
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Multi-criteria ranking and quality scoring engine for Best Shot bursts.
 *
 * Evaluates physical sharpness, physical gyro motion, inter-frame disparity,
 * radiometric exposure balance, and facial eye-openness.
 *
 * Guarantees zero synthetic manipulation: all evaluations rank strictly real captured frames.
 */
@Singleton
class BestShotScorer @Inject constructor() {

    /**
     * Scores a raw downsampled luminance buffer, gyro velocity, and optional detected faces.
     */
    fun scoreFrame(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        stride: Int = width,
        gyroAngularSpeed: Float = 0.0f,
        adjacentYPlane: ByteArray? = null,
        detectedFaces: List<DetectedFace> = emptyList(),
    ): BestShotCandidateScores {
        val sharpness = computeSharpness(yPlane, width, height, stride)
        val motion = computeMotionStability(yPlane, adjacentYPlane, width, height, stride, gyroAngularSpeed)
        val exposure = computeExposureScore(yPlane, width, height, stride)
        val eyeOpen = evaluateEyeOpenness(detectedFaces)

        val composite = if (eyeOpen != null) {
            (sharpness * 0.35f) + (motion * 0.25f) + (exposure * 0.15f) + ((eyeOpen * 100f) * 0.25f)
        } else {
            (sharpness * 0.50f) + (motion * 0.30f) + (exposure * 0.20f)
        }.coerceIn(0.0f, 100.0f)

        return BestShotCandidateScores(
            sharpnessScore = sharpness,
            motionScore = motion,
            exposureScore = exposure,
            eyeOpenScore = eyeOpen,
            compositeScore = composite,
        )
    }

    /**
     * Computes Tenengrad gradient energy (sum of squared Sobel/central differences).
     */
    fun computeSharpness(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        stride: Int = width,
    ): Float {
        if (width < 3 || height < 3 || yPlane.isEmpty()) return 0.0f

        val maxIndexNeeded = (height - 1) * stride + width
        if (yPlane.size < maxIndexNeeded) {
            // Buffer is smaller than full frame dimensions (e.g. downsampled, test dummy, or pooled buffer)
            if (yPlane.size < 3) return 0.0f
            var gradientEnergySum = 0.0
            var count = 0
            for (i in 1 until yPlane.size - 1) {
                val g = (yPlane[i + 1].toInt() and 0xFF) - (yPlane[i - 1].toInt() and 0xFF)
                gradientEnergySum += (g * g).toDouble()
                count++
            }
            if (count == 0) return 0.0f
            val meanEnergy = gradientEnergySum / count
            val rmsGradient = sqrt(meanEnergy).toFloat()
            return ((rmsGradient / 35.0f) * 100.0f).coerceIn(0.0f, 100.0f)
        }

        var gradientEnergySum = 0.0
        var count = 0

        // Subsample with step 2 for real-time responsiveness
        val step = 2
        for (y in 1 until height - 1 step step) {
            val row = y * stride
            val prevRow = (y - 1) * stride
            val nextRow = (y + 1) * stride

            for (x in 1 until width - 1 step step) {
                val gx = (yPlane[row + x + 1].toInt() and 0xFF) - (yPlane[row + x - 1].toInt() and 0xFF)
                val gy = (yPlane[nextRow + x].toInt() and 0xFF) - (yPlane[prevRow + x].toInt() and 0xFF)
                val magSq = (gx * gx + gy * gy).toDouble()
                gradientEnergySum += magSq
                count++
            }
        }

        if (count == 0) return 0.0f
        val meanEnergy = gradientEnergySum / count
        val rmsGradient = sqrt(meanEnergy).toFloat()

        // Map typical gradient RMS (0 to 45) to a normalized 0..100 sharpness score
        return ((rmsGradient / 35.0f) * 100.0f).coerceIn(0.0f, 100.0f)
    }

    /**
     * Evaluates gyro stability and photometric inter-frame difference against adjacent frame.
     */
    fun computeMotionStability(
        yPlane: ByteArray,
        adjacentYPlane: ByteArray?,
        width: Int,
        height: Int,
        stride: Int = width,
        gyroAngularSpeed: Float = 0.0f,
    ): Float {
        // 1. Gyro penalty: higher angular speed during exposure = lower stability
        val gyroPenalty = (gyroAngularSpeed * 3.5f).coerceIn(0.0f, 0.70f)
        val gyroScore = (1.0f - gyroPenalty)

        // 2. Photometric inter-frame disparity if adjacent frame provided
        val disparityScore = if (adjacentYPlane != null && adjacentYPlane.size == yPlane.size) {
            var diffSum = 0L
            var sampleCount = 0
            val step = 4
            for (y in 0 until height step step) {
                val offset = y * stride
                for (x in 0 until width step step) {
                    val idx = offset + x
                    if (idx < yPlane.size) {
                        val p1 = yPlane[idx].toInt() and 0xFF
                        val p2 = adjacentYPlane[idx].toInt() and 0xFF
                        diffSum += abs(p1 - p2)
                        sampleCount++
                    }
                }
            }
            if (sampleCount > 0) {
                val meanDiff = diffSum.toFloat() / sampleCount
                // Mean diff > 25 indicates significant subject motion
                (1.0f - (meanDiff / 40.0f)).coerceIn(0.2f, 1.0f)
            } else {
                1.0f
            }
        } else {
            1.0f
        }

        return (gyroScore * disparityScore * 100.0f).coerceIn(0.0f, 100.0f)
    }

    /**
     * Evaluates histogram clipping and optimal middle-tone luminance balance.
     */
    fun computeExposureScore(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        stride: Int = width,
    ): Float {
        if (yPlane.isEmpty() || width <= 0 || height <= 0) return 50.0f

        var lumaSum = 0L
        var clippedHighlights = 0
        var crushedShadows = 0
        var count = 0

        val step = 3
        for (y in 0 until height step step) {
            val offset = y * stride
            for (x in 0 until width step step) {
                val idx = offset + x
                if (idx < yPlane.size) {
                    val luma = yPlane[idx].toInt() and 0xFF
                    lumaSum += luma
                    if (luma >= 245) clippedHighlights++
                    if (luma <= 10) crushedShadows++
                    count++
                }
            }
        }

        if (count == 0) return 50.0f

        val meanLuma = lumaSum.toFloat() / count
        val highlightClipFraction = clippedHighlights.toFloat() / count
        val shadowClipFraction = crushedShadows.toFloat() / count

        // Penalties for over-exposure and under-exposure
        val clippingPenalty = (highlightClipFraction * 2.5f + shadowClipFraction * 1.5f).coerceIn(0.0f, 0.8f)

        // Distance from ideal mid-tone center (around 120 luma)
        val lumaDistance = abs(meanLuma - 120.0f) / 120.0f
        val balanceScore = (1.0f - (lumaDistance * 0.5f)).coerceIn(0.2f, 1.0f)

        return ((1.0f - clippingPenalty) * balanceScore * 100.0f).coerceIn(0.0f, 100.0f)
    }

    /**
     * Evaluates eye openness when face detection confidence is strong (>= 0.70).
     *
     * In multi-person photos, calculates the group eye-openness score prioritizing
     * that no person is caught blinking.
     */
    fun evaluateEyeOpenness(detectedFaces: List<DetectedFace>): Float? {
        val confidentFaces = detectedFaces.filter { it.confidence >= 0.70f }
        if (confidentFaces.isEmpty()) return null

        val eyeScores = confidentFaces.mapNotNull { it.eyeOpenScore }
        if (eyeScores.isEmpty()) return null

        // In group photos, min eye score takes precedence so nobody is blinking,
        // blended with the group average (70% min + 30% avg)
        val minScore = eyeScores.minOrNull() ?: 1.0f
        val avgScore = eyeScores.average().toFloat()
        return (minScore * 0.70f + avgScore * 0.30f).coerceIn(0.0f, 1.0f)
    }
}

/**
 * Score component breakdown for a candidate.
 */
data class BestShotCandidateScores(
    val sharpnessScore: Float,
    val motionScore: Float,
    val exposureScore: Float,
    val eyeOpenScore: Float?,
    val compositeScore: Float,
)
