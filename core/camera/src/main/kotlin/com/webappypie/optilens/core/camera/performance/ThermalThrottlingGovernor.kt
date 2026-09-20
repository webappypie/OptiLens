package com.webappypie.optilens.core.camera.performance

import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.thermal.DeviceThermalMonitor
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Coordinates thermal status and device hardware performance tier to throttle capture and analysis workloads.
 */
@Singleton
class ThermalThrottlingGovernor @Inject constructor(
    private val thermalMonitor: DeviceThermalMonitor,
) {
    private var lastAnalysisTimestampMs: Long = 0L

    private val _userWarning = MutableStateFlow<String?>(null)
    val userWarning: StateFlow<String?> = _userWarning.asStateFlow()

    /**
     * Resolves the maximum burst frame count considering both device RAM tier and thermal condition.
     */
    fun resolveMaxBurstFrames(tier: PerformanceTier): Int {
        val tierMax = when (tier) {
            PerformanceTier.ENTRY_LEVEL -> 4
            PerformanceTier.MID_RANGE -> 8
            PerformanceTier.HIGH_PERFORMANCE, PerformanceTier.FLAGSHIP -> 12
        }
        val thermalMax = thermalMonitor.policy.value.maxBurstFrames
        return min(tierMax, thermalMax)
    }

    /**
     * Resolves whether continuous histogram analysis should be active.
     */
    fun isContinuousHistogramAllowed(): Boolean {
        return !thermalMonitor.policy.value.pauseContinuousHistogram
    }

    /**
     * Resolves whether multi-frame night mode capture is allowed.
     */
    fun isMultiFrameNightAllowed(): Boolean {
        return thermalMonitor.policy.value.allowMultiFrameNight
    }

    /**
     * Returns whether the given frame should be processed by the real-time analyzer
     * according to the thermal policy target FPS.
     */
    @Synchronized
    fun shouldProcessAnalysisFrame(nowMs: Long = System.currentTimeMillis()): Boolean {
        val targetFps = thermalMonitor.policy.value.targetAnalysisFps
        if (targetFps <= 0) return false
        val minIntervalMs = 1000L / targetFps

        if (nowMs - lastAnalysisTimestampMs >= minIntervalMs) {
            lastAnalysisTimestampMs = nowMs
            return true
        }
        return false
    }

    /**
     * Updates user warning state based on the current thermal policy.
     */
    fun updateWarningState() {
        _userWarning.value = thermalMonitor.policy.value.userWarningMessage
    }

    fun currentPolicy(): ThermalDegradationPolicy = thermalMonitor.policy.value
    fun currentThermalState(): DeviceThermalState = thermalMonitor.thermalState.value
}
