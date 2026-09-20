package com.webappypie.optilens.core.camera.performance

import android.content.Context
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import com.webappypie.optilens.core.camera.thermal.DeviceThermalMonitor
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.common.performance.MemoryProfileManager
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraPerformanceTest {

    private class FakeContext : android.content.ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
    }

    private lateinit var configCache: CameraUseCaseConfigCache
    private lateinit var thermalMonitor: DeviceThermalMonitor
    private lateinit var governor: ThermalThrottlingGovernor
    private lateinit var memoryManager: MemoryProfileManager
    private lateinit var stressHarness: BatteryStressHarness

    @Before
    fun setUp() {
        configCache = CameraUseCaseConfigCache()
        thermalMonitor = DeviceThermalMonitor(FakeContext(), NoOpLogger())
        governor = ThermalThrottlingGovernor(thermalMonitor)
        memoryManager = MemoryProfileManager()
        stressHarness = BatteryStressHarness(thermalMonitor, memoryManager)
    }

    @Test
    fun `configCache correctly identifies rebind necessity and skips redundant rebinds`() {
        val config1 = CameraUseCaseConfigCache.UseCaseConfig(
            lensFacing = LensFacing.BACK,
            analysisEnabled = true,
            rawFormat = null,
            surfaceProviderHashCode = 12345,
            targetAspectRatio = 1,
        )

        // Initial config requires rebind
        assertTrue(configCache.requiresRebind(config1))
        configCache.commitActiveConfig(config1)

        // Identical config does not require rebind
        assertFalse(configCache.requiresRebind(config1))
        assertEquals(1, configCache.skippedCount)
        assertEquals(2, configCache.totalRequests)

        // Change lens facing -> requires rebind
        val config2 = config1.copy(lensFacing = LensFacing.FRONT)
        assertTrue(configCache.requiresRebind(config2))
        configCache.commitActiveConfig(config2)

        // Invalidate -> next check requires rebind
        configCache.invalidate()
        assertTrue(configCache.requiresRebind(config2))
    }

    @Test
    fun `governor resolves correct max burst frames for tiers under thermal conditions`() {
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.NORMAL)
        assertEquals(4, governor.resolveMaxBurstFrames(PerformanceTier.ENTRY_LEVEL))
        assertEquals(8, governor.resolveMaxBurstFrames(PerformanceTier.MID_RANGE))
        assertEquals(12, governor.resolveMaxBurstFrames(PerformanceTier.HIGH_PERFORMANCE))
        assertEquals(12, governor.resolveMaxBurstFrames(PerformanceTier.FLAGSHIP))

        // Moderate thermal: capped at 6
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.MODERATE)
        assertEquals(4, governor.resolveMaxBurstFrames(PerformanceTier.ENTRY_LEVEL)) // min(4, 6) = 4
        assertEquals(6, governor.resolveMaxBurstFrames(PerformanceTier.MID_RANGE)) // min(8, 6) = 6
        assertEquals(6, governor.resolveMaxBurstFrames(PerformanceTier.HIGH_PERFORMANCE)) // min(12, 6) = 6
        assertEquals(6, governor.resolveMaxBurstFrames(PerformanceTier.FLAGSHIP))

        // Severe thermal: capped at 3
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.SEVERE)
        assertEquals(3, governor.resolveMaxBurstFrames(PerformanceTier.ENTRY_LEVEL)) // min(4, 3) = 3
        assertEquals(3, governor.resolveMaxBurstFrames(PerformanceTier.MID_RANGE))
        assertEquals(3, governor.resolveMaxBurstFrames(PerformanceTier.HIGH_PERFORMANCE))
        assertEquals(3, governor.resolveMaxBurstFrames(PerformanceTier.FLAGSHIP))

        // Critical thermal: capped at 1
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.CRITICAL)
        assertEquals(1, governor.resolveMaxBurstFrames(PerformanceTier.ENTRY_LEVEL))
        assertEquals(1, governor.resolveMaxBurstFrames(PerformanceTier.MID_RANGE))
        assertEquals(1, governor.resolveMaxBurstFrames(PerformanceTier.HIGH_PERFORMANCE))
        assertEquals(1, governor.resolveMaxBurstFrames(PerformanceTier.FLAGSHIP))
    }

    @Test
    fun `governor handles thermal policies and warnings`() {
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.NORMAL)
        assertTrue(governor.isContinuousHistogramAllowed())
        assertTrue(governor.isMultiFrameNightAllowed())
        governor.updateWarningState()
        org.junit.Assert.assertNull(governor.userWarning.value)

        // Severe disables continuous histogram and multi frame night
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.SEVERE)
        assertFalse(governor.isContinuousHistogramAllowed())
        assertFalse(governor.isMultiFrameNightAllowed())

        // Critical provides user warning
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.CRITICAL)
        governor.updateWarningState()
        assertNotNull(governor.userWarning.value)
        assertTrue(governor.userWarning.value!!.contains("hot"))
    }

    @Test
    fun `governor enforces analysis frame rate throttling`() {
        // Severe state target fps is 5 -> 200ms min interval
        thermalMonitor.setSimulatedThermalState(DeviceThermalState.SEVERE)

        val t0 = 1000L
        assertTrue(governor.shouldProcessAnalysisFrame(t0))

        // 50ms later -> rejected
        assertFalse(governor.shouldProcessAnalysisFrame(t0 + 50L))

        // 150ms later -> rejected
        assertFalse(governor.shouldProcessAnalysisFrame(t0 + 150L))

        // 201ms later -> accepted
        assertTrue(governor.shouldProcessAnalysisFrame(t0 + 201L))
    }

    @Test
    fun `battery stress harness executes capture loop and verifies stability`() = runBlocking {
        var simulatedBattery = 100f

        val report = stressHarness.runStressLoop(
            config = BatteryStressHarness.StressTestConfig(shotCount = 10, intervalMs = 1L),
            batteryProvider = {
                simulatedBattery -= 0.1f
                simulatedBattery
            },
            captureSimulator = { shotIndex ->
                if (shotIndex == 5) {
                    thermalMonitor.setSimulatedThermalState(DeviceThermalState.MODERATE)
                }
            },
        )

        assertEquals(10, report.completedShots)
        assertTrue(report.isSuccess)
        assertTrue(report.isHeapStable)
        assertEquals(2, report.thermalProgression.size)
        assertEquals(DeviceThermalState.NORMAL, report.thermalProgression[0])
        assertEquals(DeviceThermalState.MODERATE, report.thermalProgression[1])
        assertTrue(report.drainPerHourPct >= 0f)
    }
}
