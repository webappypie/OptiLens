package com.webappypie.optilens.core.imaging.sr

import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState

/**
 * Execution plan computed after evaluating device hardware tier, thermal pressure, and Pro entitlement.
 */
data class SrGatedExecutionPlan(
    val permittedScale: Float,
    val is4xPermitted: Boolean,
    val preferredDelegate: String,
    val recommendedTileSize: Int,
    val allowNeuralModel: Boolean,
    val gateReason: String,
)

/**
 * Hardware and thermal policy gate regulating Super Resolution execution paths.
 *
 * Enforces:
 * - Task 7: Device-tier delegate selection (GPU/NNAPI on Flagship/HighPerformance, CPU on Mid/Entry).
 * - Task 8: Optional 4x Pro path ONLY on devices passing quality/performance and thermal gate.
 */
object SrDeviceTierGate {

    /**
     * Evaluates whether 2x or 4x Super Resolution can be safely executed.
     */
    fun evaluateGate(
        performanceTier: PerformanceTier,
        thermalState: DeviceThermalState,
        isPro: Boolean,
        requestedScale: Float = 2.0f,
    ): SrGatedExecutionPlan {
        val isThermalRestricted = thermalState == DeviceThermalState.SEVERE || thermalState == DeviceThermalState.CRITICAL
        val isThermalModerate = thermalState == DeviceThermalState.MODERATE

        // 1. Critical Thermal Condition: Downgrade or clamp to single-frame 1x / minimal 2x
        if (thermalState == DeviceThermalState.CRITICAL) {
            return SrGatedExecutionPlan(
                permittedScale = 1.0f,
                is4xPermitted = false,
                preferredDelegate = "CPU_LOW_POWER",
                recommendedTileSize = 128,
                allowNeuralModel = false,
                gateReason = "Device thermal state is CRITICAL. Heavy super-resolution suspended to prevent overheating.",
            )
        }

        // 2. Evaluate 4x Pro Path Gate
        if (requestedScale >= 3.5f) {
            val tierSupports4x = (performanceTier == PerformanceTier.FLAGSHIP || performanceTier == PerformanceTier.HIGH_PERFORMANCE)
            val thermalPermits4x = !isThermalRestricted && !isThermalModerate

            if (isPro && tierSupports4x && thermalPermits4x) {
                return SrGatedExecutionPlan(
                    permittedScale = 4.0f,
                    is4xPermitted = true,
                    preferredDelegate = if (performanceTier == PerformanceTier.FLAGSHIP) "GPU" else "NNAPI",
                    recommendedTileSize = 256,
                    allowNeuralModel = true,
                    gateReason = "4x Pro Super Resolution approved on ${performanceTier.name} hardware with optimal thermals.",
                )
            } else {
                val reason = when {
                    !isPro -> "4x Super Resolution is a Pro feature. Defaulting to production 2x SR."
                    !tierSupports4x -> "Hardware tier '${performanceTier.name}' insufficient for 4x latency budget (< 800ms). Falling back to 2x SR."
                    else -> "Device thermals are elevated (${thermalState.name}). 4x SR suspended to protect hardware. Falling back to 2x SR."
                }
                return SrGatedExecutionPlan(
                    permittedScale = 2.0f,
                    is4xPermitted = false,
                    preferredDelegate = "CPU_MULTITHREADED",
                    recommendedTileSize = 256,
                    allowNeuralModel = false,
                    gateReason = reason,
                )
            }
        }

        // 3. Standard Production 2x Path
        val delegate = when (performanceTier) {
            PerformanceTier.FLAGSHIP -> if (!isThermalModerate) "GPU" else "CPU_MULTITHREADED"
            PerformanceTier.HIGH_PERFORMANCE -> "NNAPI"
            PerformanceTier.MID_RANGE -> "CPU_MULTITHREADED"
            PerformanceTier.ENTRY_LEVEL -> "CPU_LIGHTWEIGHT"
        }

        val tileSize = when (performanceTier) {
            PerformanceTier.ENTRY_LEVEL -> 128
            else -> 256
        }

        return SrGatedExecutionPlan(
            permittedScale = 2.0f,
            is4xPermitted = false,
            preferredDelegate = delegate,
            recommendedTileSize = tileSize,
            allowNeuralModel = (performanceTier == PerformanceTier.FLAGSHIP && !isThermalModerate),
            gateReason = "Production 2x Super Resolution active via $delegate on ${performanceTier.name}.",
        )
    }
}
