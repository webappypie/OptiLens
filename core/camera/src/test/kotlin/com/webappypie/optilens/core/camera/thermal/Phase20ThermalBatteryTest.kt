package com.webappypie.optilens.core.camera.thermal

import com.webappypie.optilens.core.camera.analysis.PreprocessedFrameData
import com.webappypie.optilens.core.camera.moon.MoonDetectionState
import com.webappypie.optilens.core.camera.moon.MoonModeEngine
import com.webappypie.optilens.core.camera.tracking.RealtimeObjectTracker
import com.webappypie.optilens.core.camera.tracking.TrackingStatus
import com.webappypie.optilens.core.camera.wildlife.WildlifeDetectionState
import com.webappypie.optilens.core.camera.wildlife.WildlifeModeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gate verification test suite for Phase 20 thermal and battery constraints.
 *
 * Verifies that Moon stacking bursts, Wildlife fast bursts, and Real-time Object Tracking
 * strictly adhere to thermal limits and protect device hardware from battery drain and heat escalation.
 */
class Phase20ThermalBatteryTest {

    private lateinit var moonEngine: MoonModeEngine
    private lateinit var wildlifeEngine: WildlifeModeEngine
    private lateinit var tracker: RealtimeObjectTracker

    @Before
    fun setUp() {
        moonEngine = MoonModeEngine()
        wildlifeEngine = WildlifeModeEngine()
        tracker = RealtimeObjectTracker()
    }

    @Test
    fun `moon assist burst scales down under thermal progression to protect sensor and battery`() {
        val state = MoonDetectionState(isMoonDetected = true)

        val policyNormal = ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL)
        val policyLight = ThermalDegradationPolicy.forThermalState(DeviceThermalState.LIGHT)
        val policyModerate = ThermalDegradationPolicy.forThermalState(DeviceThermalState.MODERATE)
        val policySevere = ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE)
        val policyCritical = ThermalDegradationPolicy.forThermalState(DeviceThermalState.CRITICAL)

        assertEquals("Normal thermal condition allows full 8-frame moon stack", 8, moonEngine.computeCaptureConfig(state, policyNormal).burstFrameCount)
        assertEquals("Light thermal condition maintains 8-frame moon stack", 8, moonEngine.computeCaptureConfig(state, policyLight).burstFrameCount)
        assertEquals("Moderate thermal condition reduces moon stack to 4 frames", 4, moonEngine.computeCaptureConfig(state, policyModerate).burstFrameCount)
        assertEquals("Severe thermal condition throttles moon stack to 2 frames", 2, moonEngine.computeCaptureConfig(state, policySevere).burstFrameCount)
        assertEquals("Critical thermal condition drops to single frame fallback", 1, moonEngine.computeCaptureConfig(state, policyCritical).burstFrameCount)
    }

    @Test
    fun `wildlife burst frames throttle under moderate, severe, and critical heat`() {
        val state = WildlifeDetectionState(isDetected = true)

        val policyNormal = ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL)
        val policyModerate = ThermalDegradationPolicy.forThermalState(DeviceThermalState.MODERATE)
        val policySevere = ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE)
        val policyCritical = ThermalDegradationPolicy.forThermalState(DeviceThermalState.CRITICAL)

        val configNormal = wildlifeEngine.computeCaptureConfig(state, policyNormal)
        assertFalse(configNormal.isThermallyCapped)
        assertEquals(6, configNormal.burstFrameCount)

        val configModerate = wildlifeEngine.computeCaptureConfig(state, policyModerate)
        assertTrue(configModerate.isThermallyCapped)
        assertEquals(4, configModerate.burstFrameCount)

        val configSevere = wildlifeEngine.computeCaptureConfig(state, policySevere)
        assertTrue(configSevere.isThermallyCapped)
        assertEquals(2, configSevere.burstFrameCount)

        val configCritical = wildlifeEngine.computeCaptureConfig(state, policyCritical)
        assertTrue(configCritical.isThermallyCapped)
        assertEquals(1, configCritical.burstFrameCount)
    }

    @Test
    fun `real-time object tracking automatically suspends when device reaches severe or critical thermal load`() {
        tracker.startTracking(0.5f, 0.5f)

        val frame = PreprocessedFrameData(
            gridWidth = 80,
            gridHeight = 60,
            yGrid = IntArray(80 * 60) { 100 },
            histogramBins = FloatArray(64),
            meanLuminance = 100f,
            centerLuminance = 100f,
            peripheryLuminance = 100f,
            highlightClippingPercent = 0f,
            shadowClippingPercent = 0f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0f,
            plantScore = 0f,
            warmScore = 0f,
            timestampMs = 1000L,
        )

        // Moderate heat allows tracking
        val moderateState = tracker.update(frame, ThermalDegradationPolicy.forThermalState(DeviceThermalState.MODERATE))
        assertEquals(TrackingStatus.TRACKING, moderateState.status)

        // Severe heat triggers DISABLED_THERMAL
        val severeState = tracker.update(frame, ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE))
        assertEquals(TrackingStatus.DISABLED_THERMAL, severeState.status)
        assertEquals(0f, severeState.confidence, 0.001f)

        // Critical heat triggers DISABLED_THERMAL
        val criticalState = tracker.update(frame, ThermalDegradationPolicy.forThermalState(DeviceThermalState.CRITICAL))
        assertEquals(TrackingStatus.DISABLED_THERMAL, criticalState.status)
    }
}
