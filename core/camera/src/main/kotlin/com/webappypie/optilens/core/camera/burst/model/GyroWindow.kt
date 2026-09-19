package com.webappypie.optilens.core.camera.burst.model

import kotlin.math.sqrt

/**
 * Individual gyroscope angular velocity reading in radians/second.
 */
data class GyroSample(
    val timestampNs: Long,
    val wx: Float,
    val wy: Float,
    val wz: Float,
) {
    val angularSpeed: Float
        get() = sqrt(wx * wx + wy * wy + wz * wz)
}

/**
 * Continuous window of gyroscope angular velocity readings captured during
 * the frame exposure interval.
 *
 * Used by downstream multi-frame alignment (Phase 08) to estimate inter-frame
 * rotational homography and reject heavily motion-blurred exposures.
 */
data class GyroWindow(
    val samples: List<GyroSample> = emptyList(),
    val startTimestampNs: Long = 0L,
    val endTimestampNs: Long = 0L,
) {
    val meanAngularSpeed: Float
        get() = if (samples.isEmpty()) 0.0f else {
            samples.sumOf { it.angularSpeed.toDouble() }.toFloat() / samples.size
        }

    val maxAngularSpeed: Float
        get() = if (samples.isEmpty()) 0.0f else {
            samples.maxOf { it.angularSpeed }
        }

    val isStable: Boolean
        get() = meanAngularSpeed < 0.08f

    companion object {
        val EMPTY = GyroWindow()
    }
}
