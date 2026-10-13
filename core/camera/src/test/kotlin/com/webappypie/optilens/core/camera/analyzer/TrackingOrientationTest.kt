package com.webappypie.optilens.core.camera.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingOrientationTest {

    @Test
    fun `upright and sensor mappings are inverse for every rotation`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val (x, y) = uprightToSensor(0.2f, 0.7f, rotation)
            val (u, v) = sensorToUpright(x, y, rotation)
            assertEquals("rotation $rotation", 0.2f, u, 1e-6f)
            assertEquals("rotation $rotation", 0.7f, v, 1e-6f)
        }
    }

    @Test
    fun `portrait back camera tap near the top maps to the sensor's left edge`() {
        // Back camera held upright reports 90°: the top of the screen is the sensor's left side.
        val (x, y) = uprightToSensor(0.5f, 0.1f, 90)
        assertEquals(0.1f, x, 1e-6f)
        assertEquals(0.5f, y, 1e-6f)
    }
}
