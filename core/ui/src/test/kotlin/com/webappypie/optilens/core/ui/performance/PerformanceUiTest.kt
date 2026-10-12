package com.webappypie.optilens.core.ui.performance

import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.common.performance.FrameMetricsSnapshot
import com.webappypie.optilens.core.common.performance.MemorySnapshot
import com.webappypie.optilens.core.common.performance.StartupMetrics
import com.webappypie.optilens.core.ui.camera.CameraUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceUiTest {

    @Test
    fun `CameraUiState provides correct thermal warning messages`() {
        assertNull(CameraUiState(thermalState = DeviceThermalState.NORMAL).thermalWarningRes)
        assertNull(CameraUiState(thermalState = DeviceThermalState.LIGHT).thermalWarningRes)
        assertNull(CameraUiState(thermalState = DeviceThermalState.MODERATE).thermalWarningRes)
        assertEquals(com.webappypie.optilens.core.ui.R.string.thermal_severe, CameraUiState(thermalState = DeviceThermalState.SEVERE).thermalWarningRes)
        assertEquals(com.webappypie.optilens.core.ui.R.string.thermal_critical, CameraUiState(thermalState = DeviceThermalState.CRITICAL).thermalWarningRes)
    }

    @Test
    fun `PerformanceDashboardData models metrics accurately`() {
        val startup = StartupMetrics(
            processStartTimeMs = 1000L,
            appOnCreateTimeMs = 1040L,
            activityOnCreateTimeMs = 1110L,
            firstDrawTimeMs = 1250L,
            cameraFirstFrameTimeMs = 1380L,
            isStartupComplete = true,
            isCameraReady = true,
        )

        val frames = FrameMetricsSnapshot(
            totalFrames = 100L,
            jankFrames = 2L,
            severeJankFrames = 0L,
            p50FrameDurationMs = 14.5f,
            p90FrameDurationMs = 16.2f,
            p99FrameDurationMs = 18.0f,
        )

        val mem = MemorySnapshot(
            javaHeapAllocatedMb = 48.5f,
            javaHeapMaxMb = 256.0f,
            nativeHeapAllocatedMb = 12.0f,
            peakHeapObservedMb = 55.0f,
            isUnderPressure = false,
        )

        val data = PerformanceDashboardData(
            startupMetrics = startup,
            frameMetrics = frames,
            memorySnapshot = mem,
            thermalState = DeviceThermalState.NORMAL,
            poolHitRate = 0.85f,
            poolAcquisitions = 50L,
            poolEvictions = 2L,
            maxBurstFrames = 12,
            userThermalWarning = null,
        )

        assertEquals(250L, data.startupMetrics?.appStartupLatencyMs)
        assertEquals(380L, data.startupMetrics?.cameraReadyLatencyMs)
        assertEquals(2.0f, data.frameMetrics.jankRatePercent, 0.01f)
        assertEquals(48.5f, data.memorySnapshot.javaHeapAllocatedMb, 0.01f)
        assertEquals(0.85f, data.poolHitRate, 0.01f)
        assertEquals(12, data.maxBurstFrames)
        assertNull(data.userThermalWarning)
    }
}
