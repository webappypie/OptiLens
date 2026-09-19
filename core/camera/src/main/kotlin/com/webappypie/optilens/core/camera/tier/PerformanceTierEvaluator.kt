package com.webappypie.optilens.core.camera.tier

import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.PerformanceTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of performance tier evaluation.
 */
data class PerformanceEvaluation(
    val tier: PerformanceTier,
    val score: Int,
    val breakdown: Map<String, Int>,
)

/**
 * Evaluates device performance tier using a deterministic heuristic based on
 * camera hardware level, reported capabilities, available system RAM, and CPU cores.
 *
 * GATE INVARIANT: This heuristic never infers capabilities or tier purely from manufacturer
 * or model name. All scores are derived from hardware reports.
 */
@Singleton
class PerformanceTierEvaluator @Inject constructor() {

    fun evaluate(
        cameras: List<CameraDeviceProfile>,
        totalRamGb: Float,
        cpuCores: Int,
        apiLevel: Int,
    ): PerformanceEvaluation {
        val breakdown = mutableMapOf<String, Int>()

        val primaryBack = cameras.firstOrNull { it.lensFacing == LensFacing.BACK }
            ?: cameras.firstOrNull()

        // 1. Camera Hardware Level (Max 30 pts)
        val hwScore = when (primaryBack?.hardwareLevel) {
            CameraHardwareLevel.LEVEL_3 -> 30
            CameraHardwareLevel.FULL -> 20
            CameraHardwareLevel.LIMITED -> 8
            CameraHardwareLevel.LEGACY -> 0
            CameraHardwareLevel.EXTERNAL -> 10
            CameraHardwareLevel.UNKNOWN, null -> 0
        }
        breakdown["hardware_level"] = hwScore

        // 2. Hardware Capabilities from CameraCharacteristics (Max 30 pts)
        var capScore = 0
        if (primaryBack != null) {
            val stream = primaryBack.streamCapabilities
            if (stream.supportsRaw) capScore += 6
            if (stream.supportsBurstCapture) capScore += 4
            if (stream.supportsYuvReprocessing) capScore += 5
            if (stream.supportsPrivateReprocessing) capScore += 4
            if (stream.supportsManualSensor) capScore += 3
            if (stream.isLogicalMultiCamera || primaryBack.focalLengthsMm.size > 1) capScore += 5
            if (stream.supportsTenBitHdr || stream.supportsUltraHighResolution) capScore += 3
        }
        breakdown["capabilities"] = capScore

        // 3. System RAM (Max 25 pts)
        val ramScore = when {
            totalRamGb >= 11.0f -> 25
            totalRamGb >= 7.5f  -> 20
            totalRamGb >= 5.5f  -> 15
            totalRamGb >= 3.5f  -> 8
            else                -> 2
        }
        breakdown["ram"] = ramScore

        // 4. CPU Cores and Modern API Level (Max 15 pts)
        var cpuScore = when {
            cpuCores >= 8 -> 12
            cpuCores >= 6 -> 8
            cpuCores >= 4 -> 4
            else          -> 2
        }
        if (apiLevel >= 34) cpuScore += 3
        else if (apiLevel >= 31) cpuScore += 2
        else if (apiLevel >= 29) cpuScore += 1
        breakdown["cpu_platform"] = cpuScore

        val rawTotal = hwScore + capScore + ramScore + cpuScore

        // Determine tier with strict boundary checks
        var tier = when {
            rawTotal >= 80 -> PerformanceTier.FLAGSHIP
            rawTotal >= 60 -> PerformanceTier.HIGH_PERFORMANCE
            rawTotal >= 40 -> PerformanceTier.MID_RANGE
            else           -> PerformanceTier.ENTRY_LEVEL
        }

        // Hard constraints:
        // Flagship tier requires at least FULL or LEVEL_3 and RAW capability
        if (tier == PerformanceTier.FLAGSHIP) {
            val meetsFlagshipCriteria = primaryBack != null &&
                (primaryBack.hardwareLevel == CameraHardwareLevel.LEVEL_3 || primaryBack.hardwareLevel == CameraHardwareLevel.FULL) &&
                primaryBack.streamCapabilities.supportsRaw &&
                totalRamGb >= 7.0f
            if (!meetsFlagshipCriteria) {
                tier = PerformanceTier.HIGH_PERFORMANCE
            }
        }

        // Legacy hardware or sub-3GB RAM cannot exceed ENTRY_LEVEL
        if (primaryBack?.hardwareLevel == CameraHardwareLevel.LEGACY || totalRamGb < 3.0f) {
            tier = PerformanceTier.ENTRY_LEVEL
        }

        return PerformanceEvaluation(
            tier = tier,
            score = rawTotal,
            breakdown = breakdown,
        )
    }
}
