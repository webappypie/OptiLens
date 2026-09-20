package com.webappypie.optilens.core.ui.performance

import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.common.performance.FrameMetricsSnapshot
import com.webappypie.optilens.core.common.performance.MemorySnapshot
import com.webappypie.optilens.core.common.performance.StartupMetrics

/**
 * Aggregated telemetry data for the Quality and Performance Dashboard.
 */
data class PerformanceDashboardData(
    val startupMetrics: StartupMetrics? = null,
    val frameMetrics: FrameMetricsSnapshot = FrameMetricsSnapshot(),
    val memorySnapshot: MemorySnapshot = MemorySnapshot(),
    val thermalState: DeviceThermalState = DeviceThermalState.NORMAL,
    val poolHitRate: Float = 0f,
    val poolAcquisitions: Long = 0L,
    val poolEvictions: Long = 0L,
    val maxBurstFrames: Int = 12,
    val userThermalWarning: String? = null,
)
