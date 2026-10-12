package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.PhysicalSensorInfo
import com.webappypie.optilens.core.camera.model.SensorArrayInfo
import com.webappypie.optilens.core.camera.model.ZoomStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStopDerivationTest {

    private fun profile(
        focal: Float,
        sensorW: Float,
        sensorH: Float,
        minZoom: Float,
        maxZoom: Float,
        physical: List<PhysicalSensorInfo> = emptyList(),
    ) = CameraDeviceProfile(
        id = "0",
        lensFacing = LensFacing.BACK,
        hardwareLevel = CameraHardwareLevel.LEVEL_3,
        focalLengthsMm = listOf(focal),
        sensorInfo = SensorArrayInfo(physicalWidthMm = sensorW, physicalHeightMm = sensorH),
        minZoom = minZoom,
        maxZoom = maxZoom,
        physicalSensors = physical,
    )

    @Test
    fun `equivalent focal length uses sensor diagonal`() {
        // Redmi Note 11 Pro+ main: 5.89 mm on 8.4 x 6.3 mm -> ~24 mm equivalent
        val eq = ZoomStop.equivalentFocalLengthMm(5.89f, 8.4f, 6.3f)
        assertNotNull(eq)
        assertEquals(24.3f, eq!!, 0.3f)
        // Its 1.65 mm ultrawide on 3.70 x 2.80 mm -> ~15 mm, i.e. ~0.63x, not 1.65/5.89 = 0.28x
        val uw = ZoomStop.equivalentFocalLengthMm(1.65f, 3.70f, 2.80f)!!
        assertEquals(0.63f, uw / eq, 0.03f)
        assertNull(ZoomStop.equivalentFocalLengthMm(5.0f, 0f, 0f))
    }

    @Test
    fun `single-lens camera offers 1x optical and only digital crops`() {
        // What OptiLens can open on the Redmi: camera 0, zoom 1x-10x, no physical sub-sensors.
        val stops = ZoomStop.deriveFromProfile(profile(5.89f, 8.4f, 6.3f, minZoom = 1f, maxZoom = 10f))

        assertEquals(listOf("1x", "2x", "5x", "10x"), stops.map { it.label })
        assertTrue(stops.single { it.label == "1x" }.isOptical)
        assertTrue(stops.filter { it.label != "1x" }.none { it.isOptical })
        assertTrue("No ultrawide when the zoom range cannot go below 1x", stops.none { it.ratio < 1f })
    }

    @Test
    fun `logical camera exposes ultrawide and telephoto as optical only when its sensors back them`() {
        val physical = listOf(
            PhysicalSensorInfo(id = "2", focalLengthMm = 1.65f, sensorPhysicalWidthMm = 3.70f, sensorPhysicalHeightMm = 2.80f),
            PhysicalSensorInfo(id = "0", focalLengthMm = 5.89f, sensorPhysicalWidthMm = 8.4f, sensorPhysicalHeightMm = 6.3f),
            // ~3x telephoto: 9.4 mm on 4.0 x 3.0 mm -> ~81 mm equivalent
            PhysicalSensorInfo(id = "3", focalLengthMm = 9.4f, sensorPhysicalWidthMm = 4.0f, sensorPhysicalHeightMm = 3.0f),
        )
        val stops = ZoomStop.deriveFromProfile(
            profile(5.89f, 8.4f, 6.3f, minZoom = 0.6f, maxZoom = 10f, physical = physical)
        )

        val uw = stops.first()
        assertEquals("0.6x", uw.label)
        assertTrue(uw.isOptical)
        assertEquals("2", uw.physicalSensorId)

        val tele = stops.first { it.ratio > 2.5f && it.ratio < 4f }
        assertTrue(tele.isOptical)
        assertEquals("3", tele.physicalSensorId)

        assertFalse(stops.first { it.label == "2x" }.isOptical)
        assertFalse(stops.first { it.label == "10x" }.isOptical)
    }

    @Test
    fun `sub-1x zoom without a matching lens is labelled digital`() {
        val stops = ZoomStop.deriveFromProfile(profile(5.89f, 8.4f, 6.3f, minZoom = 0.8f, maxZoom = 4f))
        val sub = stops.first()
        assertEquals(0.8f, sub.ratio, 0.001f)
        assertFalse(sub.isOptical)
    }

    @Test
    fun `labels are compact`() {
        assertEquals("0.6x", ZoomStop.formatLabel(0.6f))
        assertEquals("2x", ZoomStop.formatLabel(2.0f))
        assertEquals("3.2x", ZoomStop.formatLabel(3.24f))
    }
}
