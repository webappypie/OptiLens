package com.webappypie.optilens.core.camera.specialized

import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Capture configuration computed by [PetModeEngine].
 */
data class PetCaptureConfig(
    val targetShutterSpeedNanos: Long,
    val recommendedIsoBias: Float,
    val furDetailProtectionStrength: Float,
    val subjectMotionDetected: Boolean,
)

/**
 * Specialized engine for Pet photography.
 *
 * Enforces:
 * 1. Fast shutter timing bias (target >= 1/250s, down to 1/1000s for active animals).
 * 2. Continuous subject motion tracking and shutter priority adaptation.
 * 3. High-frequency fur detail texture protection against aggressive denoising smearing.
 */
@Singleton
class PetModeEngine @Inject constructor() {

    companion object {
        const val MIN_SHUTTER_NANOS_STATIC = 4_000_000L // 1/250s
        const val TARGET_SHUTTER_NANOS_MOTION = 2_000_000L // 1/500s
        const val AGGRESSIVE_SHUTTER_NANOS = 1_000_000L // 1/1000s
    }

    /**
     * Computes optimal shutter timing and processing parameters for pet photography.
     */
    fun computeCaptureConfig(
        quality: QualityMetrics,
        motion: MotionState,
    ): PetCaptureConfig {
        val hasHighMotion = motion.subjectMotionLevel == SubjectMotionLevel.HIGH_MOTION ||
                motion.subjectMotionScore > 0.20f

        val shutterNanos = when {
            hasHighMotion && quality.luminance > 100f -> AGGRESSIVE_SHUTTER_NANOS
            hasHighMotion -> TARGET_SHUTTER_NANOS_MOTION
            quality.luminance > 120f -> TARGET_SHUTTER_NANOS_MOTION
            else -> MIN_SHUTTER_NANOS_STATIC
        }

        val isoBias = when {
            hasHighMotion -> 1.5f
            quality.luminance < 60f -> 1.3f
            else -> 1.0f
        }

        return PetCaptureConfig(
            targetShutterSpeedNanos = shutterNanos,
            recommendedIsoBias = isoBias,
            furDetailProtectionStrength = 0.85f,
            subjectMotionDetected = hasHighMotion,
        )
    }

    /**
     * Applies fur-detail-preserving high-frequency texture enhancement on a luminance plane.
     *
     * In high-gradient, fine-textured regions (fur coats, whiskers, pet manes),
     * applies selective micro-contrast boost. In flat, low-contrast background regions,
     * preserves smooth baseline noise profile.
     */
    fun processFurDetails(
        inputY: ByteArray,
        width: Int,
        height: Int,
        stride: Int = width,
        outY: ByteArray = ByteArray(inputY.size),
        strength: Float = 0.85f,
    ): ByteArray {
        if (inputY.isEmpty() || width < 3 || height < 3) return inputY

        val clampedStrength = strength.coerceIn(0.0f, 1.0f)
        val alpha = clampedStrength * 0.40f

        // Copy borders directly
        for (x in 0 until width) {
            outY[x] = inputY[x]
            val bottomRow = (height - 1) * stride
            outY[bottomRow + x] = inputY[bottomRow + x]
        }
        for (y in 0 until height) {
            val row = y * stride
            outY[row] = inputY[row]
            outY[row + width - 1] = inputY[row + width - 1]
        }

        // Process interior pixels with 3x3 local mean & variance analysis
        for (y in 1 until height - 1) {
            val prevRow = (y - 1) * stride
            val currRow = y * stride
            val nextRow = (y + 1) * stride

            for (x in 1 until width - 1) {
                val center = inputY[currRow + x].toInt() and 0xFF

                // 3x3 cross neighborhood for low-overhead local mean
                val n1 = inputY[prevRow + x].toInt() and 0xFF
                val n2 = inputY[nextRow + x].toInt() and 0xFF
                val n3 = inputY[currRow + x - 1].toInt() and 0xFF
                val n4 = inputY[currRow + x + 1].toInt() and 0xFF

                val localMean = (center * 2 + n1 + n2 + n3 + n4) / 6

                // Local gradient variance proxy
                val variance = abs(center - localMean)

                // Fur texture classification:
                // variance between 6 and 45 corresponds to fine hair/fur strands.
                // Extreme variance (> 60) is high-contrast edge (e.g. silhouette against sky).
                val isFurTexture = variance in 6..55

                val processed = if (isFurTexture) {
                    val detail = center - localMean
                    (center + (detail * alpha)).toInt().coerceIn(0, 255)
                } else {
                    center
                }

                outY[currRow + x] = processed.toByte()
            }
        }

        return outY
    }
}
