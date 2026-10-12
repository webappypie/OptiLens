package com.webappypie.optilens.core.ui.camera.sensor

import android.view.OrientationEventListener
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureRotationTest {

    @Test
    fun `portrait upright maps to rotation 0`() {
        assertEquals(Surface.ROTATION_0, orientationToSurfaceRotation(0, Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_0, orientationToSurfaceRotation(30, Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_0, orientationToSurfaceRotation(330, Surface.ROTATION_90))
    }

    @Test
    fun `device turned clockwise to landscape maps to rotation 270`() {
        assertEquals(Surface.ROTATION_270, orientationToSurfaceRotation(90, Surface.ROTATION_0))
    }

    @Test
    fun `upside down maps to rotation 180`() {
        assertEquals(Surface.ROTATION_180, orientationToSurfaceRotation(180, Surface.ROTATION_0))
    }

    @Test
    fun `device turned counter-clockwise to landscape maps to rotation 90`() {
        assertEquals(Surface.ROTATION_90, orientationToSurfaceRotation(270, Surface.ROTATION_0))
    }

    @Test
    fun `bucket boundaries follow the CameraX mapping`() {
        assertEquals(Surface.ROTATION_0, orientationToSurfaceRotation(44, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_270, orientationToSurfaceRotation(45, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_180, orientationToSurfaceRotation(135, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_90, orientationToSurfaceRotation(225, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_0, orientationToSurfaceRotation(315, Surface.ROTATION_90))
    }

    @Test
    fun `flat device keeps the last known rotation`() {
        assertEquals(
            Surface.ROTATION_90,
            orientationToSurfaceRotation(OrientationEventListener.ORIENTATION_UNKNOWN, Surface.ROTATION_90),
        )
    }
}
