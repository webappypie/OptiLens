package com.webappypie.optilens.core.camera.moon

import com.webappypie.optilens.core.camera.analysis.PreprocessedFrameData
import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time vision detector identifying the lunar disc in night and low-light sky frames.
 *
 * Evaluates radiometric contrast, localized compactness, circularity/ellipticity ratios,
 * and background sky darkness against false positives (e.g. street lamps, stadium lights).
 */
@Singleton
class MoonDetector @Inject constructor() {

    companion object {
        const val MIN_DISC_LUMA_THRESHOLD = 170
        const val MAX_SURROUNDING_SKY_LUMA = 55.0f
        const val MIN_DISC_PIXELS_FRACTION = 0.0003f // 0.03% of frame
        const val MAX_DISC_PIXELS_FRACTION = 0.12f   // 12% of frame
    }

    /**
     * Analyzes downsampled frame data and motion state to detect the Moon.
     */
    fun detectMoon(
        frameData: PreprocessedFrameData,
        metrics: QualityMetrics,
        motionState: MotionState,
        currentZoomRatio: Float = 1.0f,
    ): MoonDetectionState {
        val width = frameData.gridWidth
        val height = frameData.gridHeight
        val totalPixels = width * height

        // 1. Scene Guard: Background must be predominantly dark night/sky
        if (frameData.meanLuminance > 75.0f && metrics.luminance > 75.0f) {
            return MoonDetectionState.EMPTY
        }

        val yGrid = frameData.yGrid
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        var discPixelCount = 0
        var discLumaSum = 0L
        var centerOfMassXSum = 0.0
        var centerOfMassYSum = 0.0

        // 2. Locate high-luminance pixels
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val luma = yGrid[rowOffset + x]
                if (luma >= MIN_DISC_LUMA_THRESHOLD) {
                    discPixelCount++
                    discLumaSum += luma
                    centerOfMassXSum += x * luma
                    centerOfMassYSum += y * luma

                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val discFraction = discPixelCount.toFloat() / totalPixels.toFloat()
        if (discPixelCount == 0 || discFraction < MIN_DISC_PIXELS_FRACTION || discFraction > MAX_DISC_PIXELS_FRACTION) {
            return MoonDetectionState.EMPTY
        }

        val boxWidth = maxX - minX + 1
        val boxHeight = maxY - minY + 1
        val boxArea = boxWidth * boxHeight

        // 3. Compactness & Circularity check
        val aspectRatio = boxWidth.toFloat() / boxHeight.toFloat()
        if (aspectRatio !in 0.55f..1.80f) {
            // Not a circular/elliptical disc (e.g. horizontal horizon light strip or vertical neon tube)
            return MoonDetectionState.EMPTY
        }

        // Fill factor for circle within bounding box: circle area / box area ≈ π/4 ≈ 0.785
        val fillFactor = discPixelCount.toFloat() / boxArea.toFloat()
        if (fillFactor !in 0.40f..0.98f) {
            return MoonDetectionState.EMPTY
        }

        val meanDiscLuma = discLumaSum.toFloat() / discPixelCount.toFloat()
        val cx = (centerOfMassXSum / discLumaSum).toFloat()
        val cy = (centerOfMassYSum / discLumaSum).toFloat()
        val radiusPx = (boxWidth + boxHeight) / 4.0f

        // 4. Surrounding Sky Contrast Verification:
        // Sample ring immediately outside the bounding box to ensure it's surrounded by dark sky
        val outerPad = max(2, (radiusPx * 0.5f).toInt())
        var outerSkyLumaSum = 0L
        var outerSkyPixelCount = 0

        val checkMinX = max(0, minX - outerPad)
        val checkMaxX = min(width - 1, maxX + outerPad)
        val checkMinY = max(0, minY - outerPad)
        val checkMaxY = min(height - 1, maxY + outerPad)

        for (y in checkMinY..checkMaxY) {
            val rowOffset = y * width
            for (x in checkMinX..checkMaxX) {
                val isInsideBox = (x in minX..maxX) && (y in minY..maxY)
                if (!isInsideBox) {
                    outerSkyLumaSum += yGrid[rowOffset + x]
                    outerSkyPixelCount++
                }
            }
        }

        val meanOuterSkyLuma = if (outerSkyPixelCount > 0) {
            outerSkyLumaSum.toFloat() / outerSkyPixelCount.toFloat()
        } else {
            0.0f
        }

        if (meanOuterSkyLuma > MAX_SURROUNDING_SKY_LUMA) {
            // Surrounding area is too bright (e.g. broad floodlight or daytime glare)
            return MoonDetectionState.EMPTY
        }

        // 5. Circularity Metric & Confidence Computation
        val circularity = (1.0f - abs(1.0f - aspectRatio)).coerceIn(0.5f, 1.0f)
        val contrastRatio = (meanDiscLuma / (meanOuterSkyLuma.coerceAtLeast(1.0f))).coerceAtLeast(1.0f)
        val contrastConfidence = (contrastRatio / 10.0f).coerceIn(0.6f, 0.98f)
        val compositeConfidence = (0.5f * circularity + 0.5f * contrastConfidence).coerceIn(0.70f, 0.99f)

        // 6. Stability Cue Evaluation
        val stabilityCue = when {
            motionState.cameraShakeLevel == CameraShakeLevel.STABLE && motionState.gyroAngularVelocityRadPerSec < 0.20f -> {
                StabilityCue.STABLE
            }
            motionState.gyroAngularVelocityRadPerSec > 0.65f || motionState.cameraShakeLevel == CameraShakeLevel.HIGH -> {
                StabilityCue.TRIPOD_RECOMMENDED
            }
            else -> {
                StabilityCue.HOLD_STEADY
            }
        }

        // 7. Spot Exposure Recommendation
        val suggestedEvOffset = when {
            meanDiscLuma > 230.0f -> -3
            meanDiscLuma > 195.0f -> -2
            else -> -1
        }

        // 8. Suggested Tele Zoom Ratio
        val suggestedZoom = when {
            currentZoomRatio < 3.0f -> 3.0f
            currentZoomRatio < 5.0f -> 5.0f
            else -> currentZoomRatio
        }

        val normRadius = radiusPx / sqrt((width * width + height * height).toFloat())
        val roi = MoonDiscRoi(
            left = minX.toFloat() / width.toFloat(),
            top = minY.toFloat() / height.toFloat(),
            right = (maxX + 1).toFloat() / width.toFloat(),
            bottom = (maxY + 1).toFloat() / height.toFloat(),
            centerX = cx / width.toFloat(),
            centerY = cy / height.toFloat(),
            radius = normRadius,
            meanLuma = meanDiscLuma,
            circularity = circularity,
            confidence = compositeConfidence,
        )

        return MoonDetectionState(
            isMoonDetected = true,
            roi = roi,
            confidence = compositeConfidence,
            stabilityCue = stabilityCue,
            suggestedEvOffset = suggestedEvOffset,
            suggestedZoomRatio = suggestedZoom,
            timestampMs = frameData.timestampMs,
        )
    }
}
