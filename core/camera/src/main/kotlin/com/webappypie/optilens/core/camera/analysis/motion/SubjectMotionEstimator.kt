package com.webappypie.optilens.core.camera.analysis.motion

import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import kotlin.math.abs

/**
 * Estimates scene subject motion by comparing sequential downscaled luminance frames
 * while subtracting global camera rotation measured by the gyroscope.
 */
class SubjectMotionEstimator {

    private var previousGrid: IntArray? = null
    private var lastTimestampMs = 0L

    /**
     * Computes [MotionState] from current luminance grid and gyro rotational velocity.
     *
     * @param currentGrid Downscaled luminance grid (160x120).
     * @param gyroAngularVelocity Current smoothed gyro angular velocity in rad/s.
     * @param timestampMs Frame timestamp.
     */
    fun estimateMotion(
        currentGrid: IntArray,
        gyroAngularVelocity: Float,
        timestampMs: Long = System.currentTimeMillis(),
    ): MotionState {
        val prev = previousGrid
        previousGrid = currentGrid.clone()

        if (prev == null || prev.size != currentGrid.size) {
            lastTimestampMs = timestampMs
            return MotionState(
                gyroAngularVelocityRadPerSec = gyroAngularVelocity,
                cameraShakeLevel = gyroToShakeLevel(gyroAngularVelocity),
                subjectMotionScore = 0.0f,
                subjectMotionLevel = SubjectMotionLevel.STATIC,
                timestampMs = timestampMs,
            )
        }

        val pixelCount = currentGrid.size
        var sadSum = 0L

        // Sub-sample by step 2 for performance
        var sampledPixels = 0
        for (i in 0 until pixelCount step 2) {
            sadSum += abs(currentGrid[i] - prev[i])
            sampledPixels++
        }

        val meanPixelDiff = if (sampledPixels > 0) sadSum.toFloat() / sampledPixels.toFloat() else 0.0f

        // Approximate ego-motion contribution: 1 rad/s across typical FOV yields ~35 mean pixel shift on downscaled grid
        val estimatedEgoShift = (gyroAngularVelocity * 35.0f).coerceAtLeast(0.0f)
        val residualSubjectDiff = (meanPixelDiff - estimatedEgoShift).coerceAtLeast(0.0f)

        // Normalize residual diff: 0..25 to 0.0..1.0
        val subjectMotionScore = (residualSubjectDiff / 25.0f).coerceIn(0.0f, 1.0f)

        val subjectMotionLevel = when {
            residualSubjectDiff < 3.5f -> SubjectMotionLevel.STATIC
            residualSubjectDiff < 12.0f -> SubjectMotionLevel.LOW_MOTION
            else -> SubjectMotionLevel.HIGH_MOTION
        }

        val shakeLevel = gyroToShakeLevel(gyroAngularVelocity)

        return MotionState(
            gyroAngularVelocityRadPerSec = gyroAngularVelocity,
            cameraShakeLevel = shakeLevel,
            subjectMotionScore = subjectMotionScore,
            subjectMotionLevel = subjectMotionLevel,
            timestampMs = timestampMs,
        )
    }

    fun reset() {
        previousGrid = null
        lastTimestampMs = 0L
    }

    private fun gyroToShakeLevel(gyro: Float) = when {
        gyro < 0.08f -> com.webappypie.optilens.core.camera.model.CameraShakeLevel.STABLE
        gyro < 0.25f -> com.webappypie.optilens.core.camera.model.CameraShakeLevel.MODERATE
        else -> com.webappypie.optilens.core.camera.model.CameraShakeLevel.HIGH
    }
}
