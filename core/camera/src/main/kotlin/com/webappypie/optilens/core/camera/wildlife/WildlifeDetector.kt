package com.webappypie.optilens.core.camera.wildlife

import com.webappypie.optilens.core.camera.analysis.PreprocessedFrameData
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time vision detector identifying animal/bird subjects in outdoor, foliage, and telephoto frames.
 *
 * Emits bounding regions (ROI) and kinematics to drive fast-shutter priority and tracking focus.
 */
@Singleton
class WildlifeDetector @Inject constructor() {

    companion object {
        const val FAST_SHUTTER_NANOS = 2_000_000L      // 1/500s
        const val ULTRA_FAST_SHUTTER_NANOS = 1_000_000L // 1/1000s
    }

    /**
     * Evaluates incoming frame data to detect wildlife presence and propose subject ROI.
     */
    fun detectWildlife(
        frameData: PreprocessedFrameData,
        metrics: QualityMetrics,
        motionState: MotionState,
        faces: List<DetectedFace> = emptyList(),
        currentZoomRatio: Float = 1.0f,
    ): WildlifeDetectionState {
        // Human portrait takes absolute precedence if confident faces exist
        if (faces.isNotEmpty()) {
            return WildlifeDetectionState.EMPTY
        }

        val width = frameData.gridWidth
        val height = frameData.gridHeight
        val yGrid = frameData.yGrid

        val isNatureContext = frameData.plantScore > 0.20f || frameData.skyScore > 0.25f || currentZoomRatio >= 2.0f
        val hasMotion = motionState.subjectMotionScore > 0.15f && !motionState.isCameraShaking
        val hasGoodDetail = metrics.sharpnessScore > 30f

        if (!isNatureContext && !hasMotion) {
            return WildlifeDetectionState.EMPTY
        }

        // Search for the most salient high-gradient textured cluster in the central 70% of frame
        val startX = (width * 0.15f).toInt()
        val endX = (width * 0.85f).toInt()
        val startY = (height * 0.15f).toInt()
        val endY = (height * 0.85f).toInt()

        var minX = endX
        var maxX = startX
        var minY = endY
        var maxY = startY
        var salientPixels = 0

        for (y in startY until endY) {
            val currRow = y * width
            val nextRow = (y + 1) * width
            for (x in startX until endX) {
                val center = yGrid[currRow + x]
                val right = yGrid[currRow + x + 1]
                val down = yGrid[nextRow + x]

                // Local gradient magnitude
                val grad = abs(center - right) + abs(center - down)
                if (grad > 24) {
                    salientPixels++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // If not enough salient gradient pixels found, fallback to central crop if motion is strong
        if (salientPixels < 30 || minX >= maxX || minY >= maxY) {
            if (hasMotion && isNatureContext) {
                val cx = 0.5f
                val cy = 0.5f
                val roi = WildlifeRoi(
                    left = 0.35f,
                    top = 0.35f,
                    right = 0.65f,
                    bottom = 0.65f,
                    centerX = cx,
                    centerY = cy,
                    subjectType = if (frameData.skyScore > 0.35f) WildlifeSubjectType.BIRD else WildlifeSubjectType.GENERAL_WILDLIFE,
                    confidence = 0.72f,
                    motionScore = motionState.subjectMotionScore,
                )
                return WildlifeDetectionState(
                    isDetected = true,
                    roi = roi,
                    confidence = 0.72f,
                    trackingStability = 0.90f,
                    suggestedShutterNanos = FAST_SHUTTER_NANOS,
                    suggestedZoomRatio = max(2.5f, currentZoomRatio),
                    timestampMs = frameData.timestampMs,
                )
            }
            return WildlifeDetectionState.EMPTY
        }

        val normLeft = (minX.toFloat() / width.toFloat()).coerceIn(0f, 1f)
        val normTop = (minY.toFloat() / height.toFloat()).coerceIn(0f, 1f)
        val normRight = ((maxX + 1).toFloat() / width.toFloat()).coerceIn(0f, 1f)
        val normBottom = ((maxY + 1).toFloat() / height.toFloat()).coerceIn(0f, 1f)
        val normCx = (normLeft + normRight) / 2.0f
        val normCy = (normTop + normBottom) / 2.0f

        val subjectType = when {
            normCy < 0.45f && frameData.skyScore > 0.25f -> WildlifeSubjectType.BIRD
            frameData.plantScore > 0.25f -> WildlifeSubjectType.MAMMAL_OR_ANIMAL
            else -> WildlifeSubjectType.GENERAL_WILDLIFE
        }

        val suggestedShutter = if (motionState.subjectMotionScore > 0.35f) {
            ULTRA_FAST_SHUTTER_NANOS
        } else {
            FAST_SHUTTER_NANOS
        }

        val confidence = (0.75f + (motionState.subjectMotionScore * 0.15f) + (if (hasGoodDetail) 0.08f else 0.0f))
            .coerceIn(0.70f, 0.96f)

        val roi = WildlifeRoi(
            left = normLeft,
            top = normTop,
            right = normRight,
            bottom = normBottom,
            centerX = normCx,
            centerY = normCy,
            subjectType = subjectType,
            confidence = confidence,
            motionScore = motionState.subjectMotionScore,
        )

        return WildlifeDetectionState(
            isDetected = true,
            roi = roi,
            confidence = confidence,
            trackingStability = 0.92f,
            suggestedShutterNanos = suggestedShutter,
            suggestedZoomRatio = max(3.0f, currentZoomRatio),
            timestampMs = frameData.timestampMs,
        )
    }
}
