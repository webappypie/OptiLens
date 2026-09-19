package com.webappypie.optilens.core.camera.night

import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes optimal computational burst exposure duration, frame counts,
 * and filtering profiles for low-light night captures.
 */
@Singleton
class NightExposurePlanner @Inject constructor() {

    /**
     * Determines the optimal [NightExposurePlan] adapting to ambient luminance,
     * physical stabilization, subject movement, and thermal throttling limits.
     */
    fun planExposure(
        luminance: Float,
        stability: StabilityAssessment,
        subjectMotion: SubjectMotionLevel = SubjectMotionLevel.STATIC,
        thermalPolicy: ThermalDegradationPolicy = ThermalDegradationPolicy(),
        isHighContrastOrNeon: Boolean = false,
    ): NightExposurePlan {
        // 1. Base frame count by stability
        var targetFrameCount = when (stability.classification) {
            StabilityClassification.TRIPOD -> {
                if (thermalPolicy.allowDeepTripodStack) {
                    if (luminance < 15.0f) 12 else 10
                } else 6
            }
            StabilityClassification.HANDHELD_STABLE -> {
                if (luminance < 20.0f) 8 else 6
            }
            StabilityClassification.HANDHELD_MODERATE -> 4
            StabilityClassification.UNSTEADY -> 2
        }

        // 2. High subject motion overrides frame count to freeze action
        if (subjectMotion == SubjectMotionLevel.HIGH_MOTION) {
            targetFrameCount = targetFrameCount.coerceAtMost(2)
        } else if (subjectMotion == SubjectMotionLevel.LOW_MOTION) {
            targetFrameCount = targetFrameCount.coerceAtMost(6)
        }

        // 3. Apply thermal throttling ceiling
        val finalFrameCount = targetFrameCount.coerceAtMost(thermalPolicy.maxBurstFrames).coerceAtLeast(1)

        // 4. Determine target shutter speed per frame in nanoseconds
        val targetShutterNanos = when {
            subjectMotion == SubjectMotionLevel.HIGH_MOTION -> 25_000_000L // 1/40s to freeze subject
            stability.classification == StabilityClassification.TRIPOD -> {
                if (luminance <= 10.0f) 250_000_000L // 1/4s deep accumulation on tripod
                else 125_000_000L // 1/8s
            }
            stability.classification == StabilityClassification.HANDHELD_STABLE -> {
                if (luminance <= 15.0f) 80_000_000L // 1/12s firm handheld
                else 50_000_000L // 1/20s
            }
            stability.classification == StabilityClassification.HANDHELD_MODERATE -> 33_333_333L // 1/30s
            else -> 20_000_000L // 1/50s when unsteady
        }

        // 5. Estimate overall capture duration in milliseconds (shutter time + sensor readout & inter-frame overhead)
        val shutterDurationMs = targetShutterNanos / 1_000_000L
        val readoutOverheadMs = 65L // Sensor readout and pipeline overhead
        val expectedDurationMs = finalFrameCount * (shutterDurationMs + readoutOverheadMs)

        // 6. ISO gain booster calculation
        val isoBoost = when {
            luminance < 10.0f && stability.classification != StabilityClassification.TRIPOD -> 1.4f
            luminance < 25.0f -> 1.2f
            else -> 1.0f
        }

        return NightExposurePlan(
            frameCount = finalFrameCount,
            targetShutterNanos = targetShutterNanos,
            evOffsets = listOf(0),
            expectedCaptureDurationMs = expectedDurationMs,
            stabilityRequired = stability.classification,
            isoBoostFactor = isoBoost,
            enableChromaCleanup = thermalPolicy.enableHeavyFilters,
            enableHighlightProtection = isHighContrastOrNeon || luminance < 30.0f,
            conservativeSharpening = true,
        )
    }
}
