package com.webappypie.optilens.core.camera.thermal

import android.content.Context
import com.webappypie.optilens.core.logging.NoOpLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DeviceThermalMonitorTest {

    @Test
    fun fromPowerManagerStatus_mapsAllStatusCodesCorrectly() {
        assertEquals(DeviceThermalState.NORMAL, DeviceThermalState.fromPowerManagerStatus(0))
        assertEquals(DeviceThermalState.LIGHT, DeviceThermalState.fromPowerManagerStatus(1))
        assertEquals(DeviceThermalState.MODERATE, DeviceThermalState.fromPowerManagerStatus(2))
        assertEquals(DeviceThermalState.SEVERE, DeviceThermalState.fromPowerManagerStatus(3))
        assertEquals(DeviceThermalState.CRITICAL, DeviceThermalState.fromPowerManagerStatus(4))
        assertEquals(DeviceThermalState.CRITICAL, DeviceThermalState.fromPowerManagerStatus(5))
        assertEquals(DeviceThermalState.CRITICAL, DeviceThermalState.fromPowerManagerStatus(6))
        assertEquals(DeviceThermalState.NORMAL, DeviceThermalState.fromPowerManagerStatus(999))
    }

    @Test
    fun forThermalState_normal_providesFullQualityCapacity() {
        val policy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL)
        assertEquals(12, policy.maxBurstFrames)
        assertTrue(policy.enableHeavyFilters)
        assertTrue(policy.allowDeepTripodStack)
        assertFalse(policy.skipSecondaryAnalysis)
    }

    @Test
    fun forThermalState_light_provides8Frames() {
        val policy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.LIGHT)
        assertEquals(8, policy.maxBurstFrames)
        assertTrue(policy.enableHeavyFilters)
        assertTrue(policy.allowDeepTripodStack)
    }

    @Test
    fun forThermalState_moderate_disablesDeepTripodAndCapsTo4() {
        val policy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.MODERATE)
        assertEquals(4, policy.maxBurstFrames)
        assertFalse(policy.enableHeavyFilters)
        assertFalse(policy.allowDeepTripodStack)
    }

    @Test
    fun forThermalState_severe_capsTo2FramesAndDisablesHeavyFilters() {
        val policy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE)
        assertEquals(2, policy.maxBurstFrames)
        assertFalse(policy.enableHeavyFilters)
        assertFalse(policy.allowDeepTripodStack)
        assertTrue(policy.skipSecondaryAnalysis)
    }

    @Test
    fun forThermalState_critical_capsToSingleFrame() {
        val policy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.CRITICAL)
        assertEquals(1, policy.maxBurstFrames)
        assertFalse(policy.enableHeavyFilters)
        assertFalse(policy.allowDeepTripodStack)
        assertTrue(policy.skipSecondaryAnalysis)
    }

    private class FakeContext : android.content.ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
    }

    @Test
    fun simulatedThermalState_updatesMonitorStateAndPolicyFlows() {
        val monitor = DeviceThermalMonitor(FakeContext(), NoOpLogger())

        assertEquals(DeviceThermalState.NORMAL, monitor.thermalState.value)
        assertEquals(12, monitor.policy.value.maxBurstFrames)

        monitor.setSimulatedThermalState(DeviceThermalState.SEVERE)
        assertEquals(DeviceThermalState.SEVERE, monitor.thermalState.value)
        assertEquals(2, monitor.policy.value.maxBurstFrames)
        assertFalse(monitor.policy.value.enableHeavyFilters)

        monitor.setSimulatedThermalState(DeviceThermalState.NORMAL)
        assertEquals(DeviceThermalState.NORMAL, monitor.thermalState.value)
        assertEquals(12, monitor.policy.value.maxBurstFrames)
        assertTrue(monitor.policy.value.enableHeavyFilters)
    }
}
