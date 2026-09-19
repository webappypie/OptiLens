package com.webappypie.optilens.core.camera.thermal

import kotlinx.serialization.Serializable

/**
 * Hardware thermal state of the device.
 * Corresponds to Android PowerManager thermal status constants (API 29+).
 */
@Serializable
enum class DeviceThermalState {
    /** Device is operating at normal temperature. No throttling required. */
    NORMAL,

    /** Light thermal rise. Viewfinder and capture remain at full quality. */
    LIGHT,

    /** Moderate thermal throttle. Multi-frame burst counts are gently capped to prevent escalation. */
    MODERATE,

    /** Severe thermal load. Burst counts are strictly reduced, heavy multi-pass native filters skipped. */
    SEVERE,

    /** Critical thermal condition. Single-frame capture only to protect sensor and battery hardware. */
    CRITICAL;

    companion object {
        fun fromPowerManagerStatus(status: Int): DeviceThermalState = when (status) {
            0 -> NORMAL     // THERMAL_STATUS_NONE
            1 -> LIGHT      // THERMAL_STATUS_LIGHT
            2 -> MODERATE   // THERMAL_STATUS_MODERATE
            3 -> SEVERE     // THERMAL_STATUS_SEVERE
            4, 5, 6 -> CRITICAL // THERMAL_STATUS_CRITICAL, EMERGENCY, SHUTDOWN
            else -> NORMAL
        }
    }
}

/**
 * Degradation policy defining algorithmic limits under thermal pressure.
 */
@Serializable
data class ThermalDegradationPolicy(
    val thermalState: DeviceThermalState = DeviceThermalState.NORMAL,
    val maxBurstFrames: Int = 12,
    val enableHeavyFilters: Boolean = true,
    val allowDeepTripodStack: Boolean = true,
    val skipSecondaryAnalysis: Boolean = false,
) {
    companion object {
        fun forThermalState(state: DeviceThermalState): ThermalDegradationPolicy = when (state) {
            DeviceThermalState.NORMAL -> ThermalDegradationPolicy(
                thermalState = state,
                maxBurstFrames = 12,
                enableHeavyFilters = true,
                allowDeepTripodStack = true,
                skipSecondaryAnalysis = false,
            )
            DeviceThermalState.LIGHT -> ThermalDegradationPolicy(
                thermalState = state,
                maxBurstFrames = 8,
                enableHeavyFilters = true,
                allowDeepTripodStack = true,
                skipSecondaryAnalysis = false,
            )
            DeviceThermalState.MODERATE -> ThermalDegradationPolicy(
                thermalState = state,
                maxBurstFrames = 4,
                enableHeavyFilters = false,
                allowDeepTripodStack = false,
                skipSecondaryAnalysis = true,
            )
            DeviceThermalState.SEVERE -> ThermalDegradationPolicy(
                thermalState = state,
                maxBurstFrames = 2,
                enableHeavyFilters = false,
                allowDeepTripodStack = false,
                skipSecondaryAnalysis = true,
            )
            DeviceThermalState.CRITICAL -> ThermalDegradationPolicy(
                thermalState = state,
                maxBurstFrames = 1,
                enableHeavyFilters = false,
                allowDeepTripodStack = false,
                skipSecondaryAnalysis = true,
            )
        }
    }
}
