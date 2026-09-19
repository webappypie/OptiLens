package com.webappypie.optilens.core.imaging.sr

import com.webappypie.optilens.core.camera.model.ZoomStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalZoomRouterTest {

    private val multiLensStops = listOf(
        ZoomStop(ratio = 0.6f, label = "0.6x", isOptical = true, physicalSensorId = "sensor_uw"),
        ZoomStop(ratio = 1.0f, label = "1x", isOptical = true, physicalSensorId = "sensor_wide"),
        ZoomStop(ratio = 2.0f, label = "2x", isOptical = true, physicalSensorId = "sensor_tele"),
        ZoomStop(ratio = 5.0f, label = "5x", isOptical = true, physicalSensorId = "sensor_periscope"),
    )

    private val singleLensStops = listOf(
        ZoomStop(ratio = 1.0f, label = "1x", isOptical = true),
        ZoomStop(ratio = 2.0f, label = "2x", isOptical = false),
    )

    @Test
    fun `exact optical stop routes directly with zero digital degradation`() {
        val decision = OpticalZoomRouter.routeZoom(1.0f, multiLensStops)
        assertTrue(decision.isPureOptical)
        assertFalse(decision.superResEligible)
        assertEquals(1.0f, decision.digitalCropFactor, 0.001f)
        assertEquals("1x", decision.opticalBaseStop.label)
    }

    @Test
    fun `dedicated 2x telephoto stop routes optically instead of cropping from 1x wide`() {
        val decision = OpticalZoomRouter.routeZoom(2.0f, multiLensStops)
        assertTrue(decision.isPureOptical)
        assertFalse(decision.superResEligible)
        assertEquals("2x", decision.opticalBaseStop.label)
        assertEquals("sensor_tele", decision.opticalBaseStop.physicalSensorId)
    }

    @Test
    fun `intermediate 3x zoom routes to 2x telephoto lens as base rather than 1x wide`() {
        val decision = OpticalZoomRouter.routeZoom(3.0f, multiLensStops)
        assertFalse(decision.isPureOptical)
        assertTrue(decision.superResEligible)
        assertEquals("2x", decision.opticalBaseStop.label)
        // Digital crop should be 3.0 / 2.0 = 1.5x, NOT 3.0x from the 1x sensor!
        assertEquals(1.5f, decision.digitalCropFactor, 0.001f)
        assertEquals(2.0f, decision.recommendedSuperResScale, 0.001f)
    }

    @Test
    fun `single lens device at 2x uses 1x lens as base with 2x digital crop and activates SR`() {
        val decision = OpticalZoomRouter.routeZoom(2.0f, singleLensStops)
        assertFalse(decision.isPureOptical)
        assertTrue(decision.superResEligible)
        assertEquals(2.0f, decision.digitalCropFactor, 0.001f)
        assertEquals("1x", decision.opticalBaseStop.label)
    }

    @Test
    fun `large crop with 4x Pro enabled recommends 4x super resolution scale`() {
        val decision = OpticalZoomRouter.routeZoom(
            targetZoom = 4.0f,
            availableStops = singleLensStops,
            is4xProEnabled = true,
        )
        assertFalse(decision.isPureOptical)
        assertTrue(decision.superResEligible)
        assertEquals(4.0f, decision.recommendedSuperResScale, 0.001f)
    }
}
