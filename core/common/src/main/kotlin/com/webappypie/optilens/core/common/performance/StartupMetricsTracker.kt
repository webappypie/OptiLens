package com.webappypie.optilens.core.common.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of application startup and camera initialization timings.
 */
data class StartupMetrics(
    val processStartTimeMs: Long = 0L,
    val appOnCreateTimeMs: Long = 0L,
    val activityOnCreateTimeMs: Long = 0L,
    val firstDrawTimeMs: Long = 0L,
    val cameraFirstFrameTimeMs: Long = 0L,
    val isStartupComplete: Boolean = false,
    val isCameraReady: Boolean = false,
) {
    /** Total latency from process invocation to UI fully drawn. */
    val appStartupLatencyMs: Long
        get() = if (firstDrawTimeMs > 0L && processStartTimeMs > 0L) {
            firstDrawTimeMs - processStartTimeMs
        } else if (firstDrawTimeMs > 0L && appOnCreateTimeMs > 0L) {
            firstDrawTimeMs - appOnCreateTimeMs
        } else 0L

    /** Total latency from process invocation to first live camera frame rendered. */
    val cameraReadyLatencyMs: Long
        get() = if (cameraFirstFrameTimeMs > 0L && processStartTimeMs > 0L) {
            cameraFirstFrameTimeMs - processStartTimeMs
        } else if (cameraFirstFrameTimeMs > 0L && appOnCreateTimeMs > 0L) {
            cameraFirstFrameTimeMs - appOnCreateTimeMs
        } else 0L

    /** Latency from Activity initialization to first viewfinder frame. */
    val viewfinderReadyLatencyMs: Long
        get() = if (cameraFirstFrameTimeMs > 0L && activityOnCreateTimeMs > 0L) {
            cameraFirstFrameTimeMs - activityOnCreateTimeMs
        } else 0L
}

/**
 * High-precision latency tracker measuring application startup and camera-ready latency.
 *
 * Implements Phase 19 Task 1:
 * - Cold startup to interactive UI render.
 * - Time to first live camera viewfinder frame.
 * - Non-blocking latency breakdown telemetry.
 */
@Singleton
class StartupMetricsTracker @Inject constructor() {

    private val _metrics = MutableStateFlow(StartupMetrics())
    val metrics: StateFlow<StartupMetrics> = _metrics.asStateFlow()

    fun recordProcessStart(timestampMs: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(processStartTimeMs = timestampMs)
    }

    fun recordAppCreate(timestampMs: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(appOnCreateTimeMs = timestampMs)
    }

    fun recordActivityCreate(timestampMs: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(activityOnCreateTimeMs = timestampMs)
    }

    fun recordFirstDraw(timestampMs: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(
            firstDrawTimeMs = timestampMs,
            isStartupComplete = true,
        )
    }

    fun recordCameraFirstFrame(timestampMs: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(
            cameraFirstFrameTimeMs = timestampMs,
            isCameraReady = true,
        )
    }

    fun reset() {
        _metrics.value = StartupMetrics()
    }
}
