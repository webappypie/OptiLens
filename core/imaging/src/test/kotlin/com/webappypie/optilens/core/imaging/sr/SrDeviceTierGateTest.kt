package com.webappypie.optilens.core.imaging.sr

import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrDeviceTierGateTest {

    @Test
    fun `critical thermal state completely halts heavy super resolution`() {
        val plan = SrDeviceTierGate.evaluateGate(
            performanceTier = PerformanceTier.FLAGSHIP,
            thermalState = DeviceThermalState.CRITICAL,
            isPro = true,
            requestedScale = 2.0f,
        )
        assertEquals(1.0f, plan.permittedScale, 0.001f)
        assertFalse(plan.is4xPermitted)
        assertFalse(plan.allowNeuralModel)
    }

    @Test
    fun `flagship device with normal thermals approves 4x Pro path`() {
        val plan = SrDeviceTierGate.evaluateGate(
            performanceTier = PerformanceTier.FLAGSHIP,
            thermalState = DeviceThermalState.NORMAL,
            isPro = true,
            requestedScale = 4.0f,
        )
        assertEquals(4.0f, plan.permittedScale, 0.001f)
        assertTrue(plan.is4xPermitted)
        assertEquals("GPU", plan.preferredDelegate)
        assertTrue(plan.allowNeuralModel)
    }

    @Test
    fun `entry level hardware selects lightweight CPU delegate and 2x ceiling`() {
        val plan = SrDeviceTierGate.evaluateGate(
            performanceTier = PerformanceTier.ENTRY_LEVEL,
            thermalState = DeviceThermalState.NORMAL,
            isPro = false,
            requestedScale = 2.0f,
        )
        assertEquals(2.0f, plan.permittedScale, 0.001f)
        assertFalse(plan.is4xPermitted)
        assertEquals("CPU_LIGHTWEIGHT", plan.preferredDelegate)
        assertEquals(128, plan.recommendedTileSize)
    }

    @Test
    fun `free user on flagship requesting 4x is throttled to 2x Pro gate`() {
        val plan = SrDeviceTierGate.evaluateGate(
            performanceTier = PerformanceTier.FLAGSHIP,
            thermalState = DeviceThermalState.NORMAL,
            isPro = false,
            requestedScale = 4.0f,
        )
        assertEquals(2.0f, plan.permittedScale, 0.001f)
        assertFalse(plan.is4xPermitted)
    }
}
