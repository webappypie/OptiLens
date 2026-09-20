package com.webappypie.optilens.core.camera.wildlife

import com.webappypie.optilens.core.camera.bestshot.BestShotEngine
import com.webappypie.optilens.core.camera.bestshot.BestShotResult
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Specialized computational photography engine for Wildlife and Bird mode.
 *
 * Enforces:
 * 1. Fast physical shutter timing (1/500s up to 1/2000s) to freeze flight and high-speed motion.
 * 2. Optical telephoto lens routing preference.
 * 3. Thermal-aware high-speed burst planning.
 * 4. Multi-criteria Best Shot ranking to select the keeper frame without wing or motion blur.
 * 5. Plumage and fur detail micro-contrast protection without synthetic textures.
 */
@Singleton
class WildlifeModeEngine @Inject constructor(
    private val bestShotEngine: BestShotEngine,
) {
    constructor() : this(BestShotEngine())

    companion object {
        const val BASE_FAST_SHUTTER_NANOS = 2_000_000L   // 1/500s
        const val SPRINT_SHUTTER_NANOS = 1_000_000L      // 1/1000s
        const val FLIGHT_SHUTTER_NANOS = 500_000L        // 1/2000s
        const val MAX_DETAIL_DELTA_LUMA = 18
    }

    /**
     * Computes the capture configuration incorporating fast shutter speed, telephoto lens stops, and thermal limits.
     */
    fun computeCaptureConfig(
        wildlifeState: WildlifeDetectionState,
        thermalPolicy: ThermalDegradationPolicy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL),
        availableZoomStops: List<ZoomStop> = emptyList(),
    ): WildlifeCaptureConfig {
        val roi = wildlifeState.roi

        // 1. Shutter Priority Bias
        val targetShutterNanos = when {
            roi != null && roi.subjectType == WildlifeSubjectType.BIRD && roi.motionScore > 0.30f -> FLIGHT_SHUTTER_NANOS
            roi != null && roi.motionScore > 0.25f -> SPRINT_SHUTTER_NANOS
            wildlifeState.suggestedShutterNanos < BASE_FAST_SHUTTER_NANOS -> wildlifeState.suggestedShutterNanos
            else -> BASE_FAST_SHUTTER_NANOS
        }

        // 2. Telephoto Lens Routing Preference
        val teleStop = availableZoomStops
            .filter { it.isOptical && it.ratio >= 2.0f }
            .maxByOrNull { it.ratio }
        val recommendedZoom = teleStop?.ratio ?: wildlifeState.suggestedZoomRatio.coerceAtLeast(3.0f)

        // 3. Thermal Burst Sizing
        val isThermallyCapped = thermalPolicy.thermalState >= DeviceThermalState.MODERATE
        val burstFrameCount = when (thermalPolicy.thermalState) {
            DeviceThermalState.NORMAL -> 6
            DeviceThermalState.LIGHT -> 6
            DeviceThermalState.MODERATE -> 4
            DeviceThermalState.SEVERE -> 2
            DeviceThermalState.CRITICAL -> 1
        }

        val isoBias = when (targetShutterNanos) {
            FLIGHT_SHUTTER_NANOS -> 1.8f
            SPRINT_SHUTTER_NANOS -> 1.5f
            else -> 1.3f
        }

        return WildlifeCaptureConfig(
            targetShutterSpeedNanos = targetShutterNanos,
            recommendedIsoBias = isoBias,
            burstFrameCount = burstFrameCount,
            plumageFurDetailStrength = 0.85f,
            recommendedZoomRatio = recommendedZoom,
            isThermallyCapped = isThermallyCapped,
        )
    }

    /**
     * Evaluates a burst acquisition of wildlife frames and selects the best shot.
     */
    fun rankWildlifeBurst(burst: BurstResult): BestShotResult {
        return bestShotEngine.evaluateBurst(burst = burst)
    }

    /**
     * Evaluates a raw batch of wildlife frame buffers and ranks candidates.
     */
    fun rankWildlifeRawBuffers(buffers: List<ByteArray>, width: Int, height: Int): BestShotResult {
        return bestShotEngine.evaluateRawBuffers(buffers = buffers, width = width, height = height)
    }

    /**
     * Applies plumage and fur-detail-preserving high-frequency micro-contrast enhancement.
     *
     * Specifically tuned for fine feather shafts, barbules, downy plumage, and mammal coats.
     * In flat areas (sky, background blur), preserves smooth noise profile.
     */
    fun processPlumageAndFurDetails(
        inputY: ByteArray,
        width: Int,
        height: Int,
        roi: WildlifeRoi? = null,
        strength: Float = 0.85f,
        outY: ByteArray = ByteArray(inputY.size),
    ): ByteArray {
        if (inputY.isEmpty() || width < 3 || height < 3 || inputY.size < (width * height)) return inputY

        val clampedStrength = strength.coerceIn(0.0f, 1.0f)
        val alpha = clampedStrength * 0.45f

        val minX = roi?.let { max(1, (it.left * width).toInt()) } ?: 1
        val maxX = roi?.let { min(width - 2, (it.right * width).toInt()) } ?: (width - 2)
        val minY = roi?.let { max(1, (it.top * height).toInt()) } ?: 1
        val maxY = roi?.let { min(height - 2, (it.bottom * height).toInt()) } ?: (height - 2)

        System.arraycopy(inputY, 0, outY, 0, inputY.size)

        for (y in minY..maxY) {
            val prevRow = (y - 1) * width
            val currRow = y * width
            val nextRow = (y + 1) * width

            for (x in minX..maxX) {
                val center = inputY[currRow + x].toInt() and 0xFF

                val n1 = inputY[prevRow + x].toInt() and 0xFF
                val n2 = inputY[nextRow + x].toInt() and 0xFF
                val n3 = inputY[currRow + x - 1].toInt() and 0xFF
                val n4 = inputY[currRow + x + 1].toInt() and 0xFF

                val localMean = (center * 2 + n1 + n2 + n3 + n4) / 6
                val variance = abs(center - localMean)

                // Plumage/fur texture classification:
                // High frequency with moderate variance (variance in 6..45) is keratin texture
                if (variance in 6..45) {
                    val highPass = center - localMean
                    val delta = (highPass * alpha).roundToInt().coerceIn(-MAX_DETAIL_DELTA_LUMA, MAX_DETAIL_DELTA_LUMA)
                    outY[currRow + x] = (center + delta).coerceIn(0, 255).toByte()
                }
            }
        }

        return outY
    }
}
