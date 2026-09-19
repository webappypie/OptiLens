package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode

/**
 * Detailed diagnostic summary of an individual frame within an acquired burst.
 */
data class FrameInspectionDetail(
    val index: Int,
    val exposureTimeNs: Long,
    val formattedExposureTime: String,
    val iso: Int,
    val focusDistanceDiopters: Float?,
    val meanGyroSpeed: Float,
    val isStable: Boolean,
)

/**
 * Diagnostic analysis summary of a multi-frame burst sequence produced by [BurstMetadataInspector].
 */
data class BurstInspectionSummary(
    val totalFrames: Int,
    val modeUsed: CaptureStrategyMode,
    val totalDurationMs: Long,
    val minIso: Int,
    val maxIso: Int,
    val minExposureNs: Long,
    val maxExposureNs: Long,
    val meanGyroSpeed: Float,
    val maxGyroSpeed: Float,
    val hasMotionBlurRisk: Boolean,
    val isExposureBracketed: Boolean,
    val frameDetails: List<FrameInspectionDetail>,
)

/**
 * Debug metadata inspector evaluating multi-frame burst sequences for exposure consistency,
 * motion stability, and bracket spread.
 */
class BurstMetadataInspector {

    /**
     * Inspects a [BurstResult] and generates a comprehensive [BurstInspectionSummary].
     */
    fun inspect(burst: BurstResult): BurstInspectionSummary {
        val packets = burst.packets
        if (packets.isEmpty()) {
            return createEmptySummary(burst.modeUsed)
        }

        val frameDetails = packets.map { packet ->
            FrameInspectionDetail(
                index = packet.sequenceIndex,
                exposureTimeNs = packet.metadata.exposureTimeNs,
                formattedExposureTime = packet.metadata.formattedExposureTime,
                iso = packet.metadata.iso,
                focusDistanceDiopters = packet.metadata.focusDistanceDiopters,
                meanGyroSpeed = packet.gyroWindow.meanAngularSpeed,
                isStable = packet.gyroWindow.isStable,
            )
        }

        val isos = frameDetails.map { it.iso }
        val exposures = frameDetails.map { it.exposureTimeNs }
        val gyroSpeeds = frameDetails.map { it.meanGyroSpeed }

        val minIso = isos.minOrNull() ?: 100
        val maxIso = isos.maxOrNull() ?: 100
        val minExp = exposures.minOrNull() ?: 0L
        val maxExp = exposures.maxOrNull() ?: 0L
        val meanGyro = if (gyroSpeeds.isNotEmpty()) gyroSpeeds.average().toFloat() else 0f
        val maxGyro = gyroSpeeds.maxOrNull() ?: 0f

        // Bracketed if exposure times differ significantly (> 25% difference)
        val isBracketed = minExp > 0L && (maxExp - minExp) > (minExp * 0.25)
        val hasBlurRisk = maxGyro > 0.25f

        return BurstInspectionSummary(
            totalFrames = packets.size,
            modeUsed = burst.modeUsed,
            totalDurationMs = burst.diagnostics.totalBurstDurationMs,
            minIso = minIso,
            maxIso = maxIso,
            minExposureNs = minExp,
            maxExposureNs = maxExp,
            meanGyroSpeed = meanGyro,
            maxGyroSpeed = maxGyro,
            hasMotionBlurRisk = hasBlurRisk,
            isExposureBracketed = isBracketed,
            frameDetails = frameDetails,
        )
    }

    /**
     * Formats the inspection summary into a human-readable diagnostic log.
     */
    fun formatSummary(summary: BurstInspectionSummary): String {
        return buildString {
            appendLine("=== OptiLens Burst Metadata Inspector ===")
            appendLine("Mode: ${summary.modeUsed} | Frames: ${summary.totalFrames} | Duration: ${summary.totalDurationMs}ms")
            appendLine("ISO Range: ${summary.minIso}..${summary.maxIso} | Bracketed: ${summary.isExposureBracketed}")
            appendLine("Gyro Motion: mean=${String.format("%.3f", summary.meanGyroSpeed)} rad/s, max=${String.format("%.3f", summary.maxGyroSpeed)} rad/s | Blur Risk: ${summary.hasMotionBlurRisk}")
            appendLine("--- Frame Sequence ---")
            summary.frameDetails.forEach { frame ->
                appendLine("  [#${frame.index}] Exp: ${frame.formattedExposureTime} | ISO: ${frame.iso} | Gyro: ${String.format("%.3f", frame.meanGyroSpeed)} rad/s | Stable: ${frame.isStable}")
            }
            appendLine("=========================================")
        }
    }

    private fun createEmptySummary(mode: CaptureStrategyMode) = BurstInspectionSummary(
        totalFrames = 0,
        modeUsed = mode,
        totalDurationMs = 0L,
        minIso = 100,
        maxIso = 100,
        minExposureNs = 0L,
        maxExposureNs = 0L,
        meanGyroSpeed = 0f,
        maxGyroSpeed = 0f,
        hasMotionBlurRisk = false,
        isExposureBracketed = false,
        frameDetails = emptyList(),
    )
}
